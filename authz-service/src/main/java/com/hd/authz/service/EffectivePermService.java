package com.hd.authz.service;

import com.hd.authz.domain.Menu;
import com.hd.authz.domain.Permission;
import com.hd.authz.domain.UserEntity;
import com.hd.authz.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Computes the effective permissions for a subject (user) including their source path:
 *   - 직접부여: 해당 user에 직접 부여된 permission
 *   - 그룹경유: 사용자 그룹(UG)을 통해
 *   - 부서상속: dept(D)에 부여된 permission이 user에게 상속
 *   - 회사상속: company(C)에 부여된 permission이 user에게 상속
 *   - 폴더상속: 폴더(F)에 부여되어 자손 리프로 전개됨
 *
 * 스펙 §10.2/§10.5 — "권한 출처 색상 배지".
 */
@Service
@RequiredArgsConstructor
public class EffectivePermService {

    private final UserRepo userRepo;
    private final UserGroupMapRepo userGroupMapRepo;
    private final PermissionRepo permissionRepo;
    private final MenuRepo menuRepo;

    public Map<String, Object> forUser(String systemCd, String userId) {
        UserEntity u = userRepo.findById(userId).orElseThrow();
        Map<Long, String> menuTypes = new HashMap<>();
        Map<Long, List<Long>> children = new HashMap<>();
        menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc(systemCd).forEach(m -> {
            menuTypes.put(m.getMenuId(), m.getMenuType());
            children.computeIfAbsent(m.getParentMenuId(), k -> new ArrayList<>()).add(m.getMenuId());
        });

        // map menuId -> action -> source list
        Map<Long, Map<String, List<Map<String, Object>>>> grid = new TreeMap<>();

        addPermsWithSource(grid, "DIRECT_USER",
                permissionRepo.findBySystemCdAndSubjectTypeAndSubjectId(systemCd, "U", userId),
                children, menuTypes);

        // user groups
        for (Long gid : userGroupMapRepo.findGroupIdsByUserId(userId)) {
            addPermsWithSource(grid, "USER_GROUP:" + gid,
                    permissionRepo.findBySystemCdAndSubjectTypeAndSubjectId(systemCd, "UG", String.valueOf(gid)),
                    children, menuTypes);
        }
        // dept
        addPermsWithSource(grid, "DEPT:" + u.getDeptId(),
                permissionRepo.findBySystemCdAndSubjectTypeAndSubjectId(systemCd, "D", u.getDeptId()),
                children, menuTypes);
        // company
        addPermsWithSource(grid, "COMPANY:" + u.getCompanyCd(),
                permissionRepo.findBySystemCdAndSubjectTypeAndSubjectId(systemCd, "C", u.getCompanyCd()),
                children, menuTypes);

        // build summary
        long uniqueMenus = grid.size();
        long directs = grid.values().stream().flatMap(m -> m.values().stream()).flatMap(List::stream)
                .filter(s -> "DIRECT_USER".equals(s.get("source"))).count();
        long viaGroup = grid.values().stream().flatMap(m -> m.values().stream()).flatMap(List::stream)
                .filter(s -> ((String) s.get("source")).startsWith("USER_GROUP")).count();
        long viaDept = grid.values().stream().flatMap(m -> m.values().stream()).flatMap(List::stream)
                .filter(s -> ((String) s.get("source")).startsWith("DEPT")).count();
        long viaCompany = grid.values().stream().flatMap(m -> m.values().stream()).flatMap(List::stream)
                .filter(s -> ((String) s.get("source")).startsWith("COMPANY")).count();
        long folderInherited = grid.values().stream().flatMap(m -> m.values().stream()).flatMap(List::stream)
                .filter(s -> Boolean.TRUE.equals(s.get("via_folder"))).count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("system_cd", systemCd);
        out.put("user_id", userId);
        out.put("company_cd", u.getCompanyCd());
        out.put("dept_id", u.getDeptId());
        out.put("user_nm", u.getUserNm());
        out.put("summary", Map.of(
                "menu_count", uniqueMenus,
                "direct", directs,
                "via_group", viaGroup,
                "via_dept", viaDept,
                "via_company", viaCompany,
                "via_folder", folderInherited
        ));

        // build menu rows
        List<Map<String, Object>> menus = new ArrayList<>();
        Map<Long, Menu> menuById = new HashMap<>();
        menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc(systemCd).forEach(m -> menuById.put(m.getMenuId(), m));
        for (Map.Entry<Long, Map<String, List<Map<String, Object>>>> e : grid.entrySet()) {
            Menu menu = menuById.get(e.getKey());
            if (menu == null || !"M".equals(menu.getMenuType())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("menu_id", menu.getMenuId());
            row.put("menu_nm", menu.getMenuNm());
            row.put("menu_cd", menu.getMenuCd());
            row.put("actions", e.getValue());
            menus.add(row);
        }
        out.put("menus", menus);
        return out;
    }

    private void addPermsWithSource(Map<Long, Map<String, List<Map<String, Object>>>> grid,
                                    String source, List<Permission> perms,
                                    Map<Long, List<Long>> children, Map<Long, String> menuTypes) {
        for (Permission p : perms) {
            Long target = p.getTargetId();
            String type = menuTypes.getOrDefault(target, "M");
            List<Long> leaves = "F".equals(type) ? expandFolder(target, children, menuTypes) : List.of(target);
            for (Long leaf : leaves) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("source", source);
                entry.put("perm_id", p.getPermId());
                entry.put("via_folder", "F".equals(type));
                entry.put("folder_menu_id", "F".equals(type) ? target : null);
                grid.computeIfAbsent(leaf, k -> new TreeMap<>())
                    .computeIfAbsent(p.getActionCd(), k -> new ArrayList<>())
                    .add(entry);
            }
        }
    }

    private List<Long> expandFolder(Long folderId, Map<Long, List<Long>> children, Map<Long, String> types) {
        List<Long> out = new ArrayList<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(folderId);
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if ("M".equals(types.get(cur))) out.add(cur);
            else children.getOrDefault(cur, List.of()).forEach(stack::push);
        }
        return out;
    }
}
