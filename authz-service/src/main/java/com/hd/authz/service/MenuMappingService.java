package com.hd.authz.service;

import com.hd.authz.cache.PermCacheService;
import com.hd.authz.domain.*;
import com.hd.authz.repo.*;
import com.hd.authz.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Manages menu/menu-impl/menu-action/menu-action-api lifecycle and
 * emits the appropriate change events for the sync worker (spec §8.1).
 */
@Service
@RequiredArgsConstructor
public class MenuMappingService {

    private final MenuRepo menuRepo;
    private final MenuImplRepo menuImplRepo;
    private final MenuActionRepo menuActionRepo;
    private final MenuActionApiRepo menuActionApiRepo;
    private final ApiRepo apiRepo;
    private final PermissionRepo permissionRepo;
    private final PermissionService permissionService;
    private final PermCacheService cacheService;

    @Transactional
    public Menu saveMenu(Menu m, String actor) {
        boolean isNew = m.getMenuId() == null;
        m.setUpdatedBy(actor);
        if (isNew) m.setCreatedBy(actor);
        Menu saved = menuRepo.save(m);
        emitTreeChange(saved.getSystemCd(), saved.getMenuId(), isNew ? "ADD" : "UPDATE");
        return saved;
    }

    @Transactional
    public void deleteMenu(Long menuId, String actor) {
        Menu m = menuRepo.findById(menuId).orElseThrow();
        // remove dependents first
        menuActionApiRepo.findByMenuId(menuId).forEach(menuActionApiRepo::delete);
        menuActionRepo.findByMenuId(menuId).forEach(menuActionRepo::delete);
        menuImplRepo.findById(menuId).ifPresent(menuImplRepo::delete);
        permissionRepo.findBySystemCdAndTargetTypeAndTargetId(m.getSystemCd(), "M", menuId).forEach(p ->
                permissionRepo.delete(p));
        menuRepo.delete(m);
        emitTreeChange(m.getSystemCd(), menuId, "DELETE");
    }

    @Transactional
    public MenuImpl saveMenuImpl(MenuImpl impl) {
        return menuImplRepo.save(impl);
    }

    @Transactional
    public void toggleMenuAction(Long menuId, String actionCd, boolean enabled) {
        Menu m = menuRepo.findById(menuId).orElseThrow();
        if (!"M".equals(m.getMenuType())) {
            throw new IllegalArgumentException("Actions allowed only on leaf menus");
        }
        MenuActionId id = new MenuActionId();
        id.setMenuId(menuId);
        id.setActionCd(actionCd);
        if (enabled) {
            menuActionRepo.save(MenuAction.builder().menuId(menuId).actionCd(actionCd).build());
        } else {
            menuActionApiRepo.findByMenuIdAndActionCd(menuId, actionCd).forEach(menuActionApiRepo::delete);
            menuActionRepo.deleteById(id);
        }
        permissionService.emit("MENU_ACTION_CHANGE", m.getSystemCd(), null, null, Map.of(
                "menu_id", menuId, "action_cd", actionCd, "enabled", enabled));
    }

    @Transactional
    public void mapActionApi(Long menuId, String actionCd, Long apiId) {
        Menu m = menuRepo.findById(menuId).orElseThrow();
        // ensure menu_action exists
        MenuActionId mai = new MenuActionId(); mai.setMenuId(menuId); mai.setActionCd(actionCd);
        if (!menuActionRepo.existsById(mai)) {
            menuActionRepo.save(MenuAction.builder().menuId(menuId).actionCd(actionCd).build());
        }
        menuActionApiRepo.save(MenuActionApi.builder()
                .menuId(menuId).actionCd(actionCd).apiId(apiId).build());
        emitMenuActionApiChange(m.getSystemCd(), menuId);
    }

    @Transactional
    public void unmapActionApi(Long menuId, String actionCd, Long apiId) {
        MenuActionApiId id = new MenuActionApiId();
        id.setMenuId(menuId); id.setActionCd(actionCd); id.setApiId(apiId);
        menuActionApiRepo.deleteById(id);
        Menu m = menuRepo.findById(menuId).orElseThrow();
        emitMenuActionApiChange(m.getSystemCd(), menuId);
    }

    private void emitTreeChange(String systemCd, Long menuId, String op) {
        permissionService.emit("MENU_TREE_CHANGE", systemCd, null, null, Map.of(
                "menu_id", menuId, "op", op));
    }

    private void emitMenuActionApiChange(String systemCd, Long menuId) {
        permissionService.emit("MENU_ACTION_API_CHANGE", systemCd, null, null, Map.of("menu_id", menuId));
    }

    /** 10.8.2 — recommend APIs whose URL prefix matches the menu's route_path. */
    public List<Map<String, Object>> recommendApis(String systemCd, Long menuId, String actionCd, boolean unmappedOnly, String methodFilter) {
        MenuImpl impl = menuImplRepo.findById(menuId).orElse(null);
        // First-segment match: /api/{domain} where {domain} is the first segment of route_path.
        String prefix = null;
        if (impl != null && impl.getRoutePath() != null) {
            List<String> segs = com.hd.authz.common.UrlUtils.segments(impl.getRoutePath());
            if (!segs.isEmpty()) prefix = "/api/" + segs.get(0);
        }
        Set<Long> mappedApis = new HashSet<>();
        if (unmappedOnly) {
            menuActionApiRepo.findAll().forEach(maa -> mappedApis.add(maa.getApiId()));
        }
        List<ApiEntity> apis = apiRepo.findBySystemCd(systemCd);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ApiEntity a : apis) {
            if (methodFilter != null && !methodFilter.isBlank() && !a.getHttpMethod().equalsIgnoreCase(methodFilter)) continue;
            if (unmappedOnly && mappedApis.contains(a.getApiId())) continue;
            boolean recommended = prefix != null && a.getUrlPattern() != null && a.getUrlPattern().startsWith(prefix);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("api_id", a.getApiId());
            row.put("http_method", a.getHttpMethod());
            row.put("url_pattern", a.getUrlPattern());
            row.put("service_nm", a.getServiceNm());
            row.put("status", a.getStatus());
            row.put("recommended", recommended);
            row.put("already_mapped",
                    !menuActionApiRepo.findByMenuIdAndActionCd(menuId, actionCd).stream()
                            .filter(maa -> maa.getApiId().equals(a.getApiId()))
                            .toList().isEmpty());
            out.add(row);
        }
        // recommended first
        out.sort((x, y) -> Boolean.compare((Boolean) y.get("recommended"), (Boolean) x.get("recommended")));
        return out;
    }
}
