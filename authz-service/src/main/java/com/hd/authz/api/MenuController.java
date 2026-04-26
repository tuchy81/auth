package com.hd.authz.api;

import com.hd.authz.domain.*;
import com.hd.authz.repo.*;
import com.hd.authz.service.MenuMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Menu lifecycle (spec §10.8): TB_MENU + TB_MENU_IMPL + TB_MENU_ACTION + TB_MENU_ACTION_API.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MenuController {

    private final MenuRepo menuRepo;
    private final MenuImplRepo menuImplRepo;
    private final MenuActionRepo menuActionRepo;
    private final MenuActionApiRepo menuActionApiRepo;
    private final ApiRepo apiRepo;
    private final MenuMappingService mappingService;

    // ---- tree ----
    @GetMapping("/systems/{system}/menus")
    public List<Menu> listMenus(@PathVariable String system) {
        return menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc(system);
    }

    @GetMapping("/menus/{id}")
    public Menu getMenu(@PathVariable Long id) {
        return menuRepo.findById(id).orElseThrow();
    }

    @PostMapping("/menus")
    public Menu saveMenu(@RequestBody Menu m,
                         @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
        return mappingService.saveMenu(m, actor);
    }

    @PutMapping("/menus/{id}")
    public Menu updateMenu(@PathVariable Long id, @RequestBody Menu m,
                           @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
        m.setMenuId(id);
        return mappingService.saveMenu(m, actor);
    }

    @DeleteMapping("/menus/{id}")
    public Map<String, Object> deleteMenu(@PathVariable Long id,
                                          @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
        mappingService.deleteMenu(id, actor);
        return Map.of("ok", true);
    }

    // ---- impl ----
    @GetMapping("/menus/{id}/impl")
    public MenuImpl getImpl(@PathVariable Long id) {
        return menuImplRepo.findById(id).orElse(null);
    }

    @PutMapping("/menus/{id}/impl")
    public MenuImpl saveImpl(@PathVariable Long id, @RequestBody MenuImpl impl) {
        impl.setMenuId(id);
        return mappingService.saveMenuImpl(impl);
    }

    // ---- menu actions ----
    @GetMapping("/menus/{id}/actions")
    public List<MenuAction> getMenuActions(@PathVariable("id") Long menuId) {
        return menuActionRepo.findByMenuId(menuId);
    }

    @PostMapping("/menus/{id}/actions/{action}")
    public Map<String, Object> enableAction(@PathVariable("id") Long menuId, @PathVariable("action") String action) {
        mappingService.toggleMenuAction(menuId, action, true);
        return Map.of("ok", true);
    }

    @DeleteMapping("/menus/{id}/actions/{action}")
    public Map<String, Object> disableAction(@PathVariable("id") Long menuId, @PathVariable("action") String action) {
        mappingService.toggleMenuAction(menuId, action, false);
        return Map.of("ok", true);
    }

    // ---- menu action -> api ----
    @GetMapping("/menus/{id}/mappings")
    public Map<String, Object> mappingsByMenu(@PathVariable("id") Long menuId) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        var mappings = menuActionApiRepo.findByMenuId(menuId);
        Map<Long, ApiEntity> idx = new HashMap<>();
        // build api index for those referenced
        mappings.forEach(m -> {
            apiRepo.findById(m.getApiId()).ifPresent(a -> idx.put(a.getApiId(), a));
        });
        for (MenuActionApi m : mappings) {
            ApiEntity a = idx.get(m.getApiId());
            if (a == null) continue;
            grouped.computeIfAbsent(m.getActionCd(), k -> new ArrayList<>())
                   .add(Map.of("api_id", a.getApiId(),
                               "http_method", a.getHttpMethod(),
                               "url_pattern", a.getUrlPattern(),
                               "service_nm", a.getServiceNm()));
        }
        return Map.of("by_action", grouped);
    }

    @PostMapping("/menus/{id}/actions/{action}/apis/{apiId}")
    public Map<String, Object> mapApi(@PathVariable("id") Long menuId,
                                      @PathVariable("action") String actionCd,
                                      @PathVariable Long apiId) {
        mappingService.mapActionApi(menuId, actionCd, apiId);
        return Map.of("ok", true);
    }

    @DeleteMapping("/menus/{id}/actions/{action}/apis/{apiId}")
    public Map<String, Object> unmapApi(@PathVariable("id") Long menuId,
                                        @PathVariable("action") String actionCd,
                                        @PathVariable Long apiId) {
        mappingService.unmapActionApi(menuId, actionCd, apiId);
        return Map.of("ok", true);
    }

    /** spec §10.8.2 — recommend APIs (URL prefix match) */
    @GetMapping("/menus/{id}/recommend-apis")
    public List<Map<String, Object>> recommend(@PathVariable("id") Long menuId,
                                               @RequestParam(value = "action_cd", required = false, defaultValue = "R") String actionCd,
                                               @RequestParam(value = "system_cd") String systemCd,
                                               @RequestParam(value = "unmapped_only", defaultValue = "false") boolean unmappedOnly,
                                               @RequestParam(value = "method", required = false) String method) {
        return mappingService.recommendApis(systemCd, menuId, actionCd, unmappedOnly, method);
    }
}
