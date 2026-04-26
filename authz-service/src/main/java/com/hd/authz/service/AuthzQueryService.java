package com.hd.authz.service;

import com.hd.authz.cache.PermCacheService;
import com.hd.authz.cache.ShardKeyBuilder;
import com.hd.authz.domain.Menu;
import com.hd.authz.domain.MenuImpl;
import com.hd.authz.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthzQueryService {

    private final PermCacheService cache;
    private final ApiMetaService apiMetaService;
    private final MenuRepo menuRepo;
    private final MenuImplRepo menuImplRepo;
    private final ActionRepo actionRepo;
    private final UserRepo userRepo;

    /** spec §7.1.1 single check */
    public boolean check(String systemCd, String companyCd, String deptId, String userId,
                         String method, String apiUrl) {
        ApiMetaService.ApiMeta meta = apiMetaService.resolve(systemCd, method, apiUrl);
        if (meta == null) return false;
        return cache.checkApi(systemCd, companyCd, deptId, userId, method, apiUrl, meta.apiId());
    }

    /** spec §7.1.4 batch */
    public List<Boolean> checkBatch(String systemCd, String companyCd, String deptId, String userId,
                                    List<Map<String, String>> items) {
        return items.stream()
                .map(it -> check(systemCd, companyCd, deptId, userId, it.get("method"), it.get("api_url")))
                .toList();
    }

    /** spec §7.1.2 — actions allowed for a leaf menu */
    public Set<String> menuActions(String systemCd, String companyCd, String deptId, String userId, Long menuId) {
        Set<String> actions = new TreeSet<>();
        actionRepo.findBySystemCdOrderBySortOrder(systemCd).forEach(a -> {
            String[] keys = {
                    ShardKeyBuilder.menuActionCompanyKey(systemCd, companyCd, a.getActionCd()),
                    ShardKeyBuilder.menuActionDeptKey(systemCd, companyCd, deptId, a.getActionCd()),
                    ShardKeyBuilder.menuActionUserKey(systemCd, userId, a.getActionCd())
            };
            String mid = String.valueOf(menuId);
            for (String k : keys) {
                if (cache.lookupSet(k).contains(mid)) {
                    actions.add(a.getActionCd());
                    return;
                }
            }
        });
        return actions;
    }

    /** spec §7.1.3 — menu tree pruned by user permissions */
    public List<Map<String, Object>> menuTree(String systemCd, String companyCd, String deptId, String userId) {
        List<Menu> all = menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc(systemCd);
        Set<Long> accessibleLeaves = accessibleLeafMenus(systemCd, companyCd, deptId, userId);
        Map<Long, MenuImpl> impls = menuImplRepo.findAll().stream()
                .filter(i -> i.getMenuId() != null)
                .collect(Collectors.toMap(MenuImpl::getMenuId, i -> i, (a, b) -> a));

        Map<Long, List<Menu>> byParent = new HashMap<>();
        all.forEach(m -> byParent.computeIfAbsent(m.getParentMenuId(), k -> new ArrayList<>()).add(m));

        return buildTreeNodes(byParent.getOrDefault(null, List.of()), byParent, impls, accessibleLeaves, systemCd, companyCd, deptId, userId);
    }

    private List<Map<String, Object>> buildTreeNodes(List<Menu> nodes,
                                                     Map<Long, List<Menu>> byParent,
                                                     Map<Long, MenuImpl> impls,
                                                     Set<Long> accessibleLeaves,
                                                     String systemCd, String companyCd, String deptId, String userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Menu m : nodes) {
            if (!"A".equals(m.getStatus())) continue;
            if ("M".equals(m.getMenuType())) {
                if (!accessibleLeaves.contains(m.getMenuId())) continue;
                Map<String, Object> n = baseNode(m, impls);
                n.put("actions", menuActions(systemCd, companyCd, deptId, userId, m.getMenuId()));
                result.add(n);
            } else if ("L".equals(m.getMenuType())) {
                Map<String, Object> n = baseNode(m, impls);
                result.add(n);
            } else if ("F".equals(m.getMenuType())) {
                List<Map<String, Object>> children = buildTreeNodes(
                        byParent.getOrDefault(m.getMenuId(), List.of()),
                        byParent, impls, accessibleLeaves, systemCd, companyCd, deptId, userId);
                if (!children.isEmpty()) {
                    Map<String, Object> n = baseNode(m, impls);
                    n.put("children", children);
                    result.add(n);
                }
            }
        }
        return result;
    }

    private Map<String, Object> baseNode(Menu m, Map<Long, MenuImpl> impls) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("menu_id", m.getMenuId());
        n.put("menu_type", m.getMenuType());
        n.put("menu_nm", m.getMenuNm());
        n.put("icon", m.getIcon());
        MenuImpl impl = impls.get(m.getMenuId());
        if (impl != null) {
            n.put("route_path", impl.getRoutePath());
            n.put("external_url", impl.getExternalUrl());
        }
        return n;
    }

    public Set<Long> accessibleLeafMenus(String systemCd, String companyCd, String deptId, String userId) {
        Set<Long> ids = new HashSet<>();
        actionRepo.findBySystemCdOrderBySortOrder(systemCd).forEach(a -> {
            ids.addAll(cache.lookupSet(ShardKeyBuilder.menuActionCompanyKey(systemCd, companyCd, a.getActionCd()))
                    .stream().map(Long::valueOf).toList());
            ids.addAll(cache.lookupSet(ShardKeyBuilder.menuActionDeptKey(systemCd, companyCd, deptId, a.getActionCd()))
                    .stream().map(Long::valueOf).toList());
            ids.addAll(cache.lookupSet(ShardKeyBuilder.menuActionUserKey(systemCd, userId, a.getActionCd()))
                    .stream().map(Long::valueOf).toList());
        });
        return ids;
    }
}
