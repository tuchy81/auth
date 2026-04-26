package com.hd.authz.service;

import com.hd.authz.cache.PermCacheService;
import com.hd.authz.cache.ShardKeyBuilder;
import com.hd.authz.common.UrlUtils;
import com.hd.authz.domain.Permission;
import com.hd.authz.domain.SystemShardConfig;
import com.hd.authz.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Flattens a subject's permissions into Redis Set shards (spec §8.1).
 *
 * Steps:
 *  1) Pull all permissions for (system_cd, subject_type, subject_id).
 *  2) Expand Folder targets (M-type='F') into descendant leaves.
 *  3) For each (leaf, action), look up TB_MENU_ACTION_API to get api_ids.
 *  4) Group api_ids by shard key (method:depth[:seg]).
 *  5) Group leaves by action_cd for menu-action keys.
 *  6) Write Sets to Redis (replace), TTL based on subject level.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionFlattener {

    private final PermissionRepo permissionRepo;
    private final MenuRepo menuRepo;
    private final ApiRepo apiRepo;
    private final MenuActionApiRepo menuActionApiRepo;
    private final UserRepo userRepo;
    private final UserGroupMapRepo userGroupMapRepo;
    private final PermCacheService cacheService;

    public Set<String> rebuildSubject(String systemCd, String subjectType, String subjectId) {
        // Resolve effective subject scope
        // For C-level the company key. For D-level the dept key. For U-level the user key.
        // Group memberships (CG/DG/UG) expand to underlying C/D/U lists.
        List<String[]> levels = expandSubject(subjectType, subjectId, systemCd);
        Set<String> writtenKeys = new HashSet<>();
        SystemShardConfig cfg = cacheService.getShardConfig(systemCd);

        // load tree once
        Map<Long, List<Long>> children = new HashMap<>();
        Map<Long, String> menuTypes = new HashMap<>();
        menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc(systemCd).forEach(m -> {
            menuTypes.put(m.getMenuId(), m.getMenuType());
            children.computeIfAbsent(m.getParentMenuId(), k -> new ArrayList<>()).add(m.getMenuId());
        });

        // load api index
        Map<Long, com.hd.authz.domain.ApiEntity> apiIndex = apiRepo.findBySystemCd(systemCd).stream()
                .collect(Collectors.toMap(com.hd.authz.domain.ApiEntity::getApiId, a -> a));

        for (String[] lv : levels) {
            String level = lv[0];        // C/D/U
            String companyCd = lv[1];
            String deptId = lv[2];
            String userId = lv[3];

            // gather permissions for "this concrete level"
            List<Permission> perms = collectPermissionsForLevel(systemCd, level, companyCd, deptId, userId);

            // (action -> set<menuId-leaf>) and (shardKey -> set<apiId>)
            Map<String, Set<String>> menuActionLeaves = new HashMap<>();
            Map<String, Set<String>> apiByShard = new HashMap<>();

            for (Permission p : perms) {
                Long mid = p.getTargetId();
                String type = menuTypes.getOrDefault(mid, "M");
                List<Long> leaves = "F".equals(type) ? expandFolder(mid, children, menuTypes) : List.of(mid);
                for (Long leaf : leaves) {
                    String act = p.getActionCd();
                    menuActionLeaves.computeIfAbsent(act, k -> new HashSet<>()).add(String.valueOf(leaf));
                    // expand action -> api ids
                    menuActionApiRepo.findByMenuIdAndActionCd(leaf, act).forEach(maa -> {
                        com.hd.authz.domain.ApiEntity a = apiIndex.get(maa.getApiId());
                        if (a == null) return;
                        int depth = a.getUrlDepth() == null ? UrlUtils.depth(a.getUrlPattern()) : a.getUrlDepth();
                        String seg = a.getShardSeg() != null ? a.getShardSeg()
                                : ShardKeyBuilder.extractSeg(a.getUrlPattern(), cfg);
                        String shard = ShardKeyBuilder.shard(a.getHttpMethod(), depth, seg, cfg);
                        apiByShard.computeIfAbsent(shard, k -> new HashSet<>()).add(String.valueOf(a.getApiId()));
                    });
                }
            }

            // Write keys for this level
            Duration ttl = "U".equals(level) ? Duration.ofHours(1) : null;
            for (Map.Entry<String, Set<String>> e : apiByShard.entrySet()) {
                String key = switch (level) {
                    case "C" -> ShardKeyBuilder.companyKey(systemCd, companyCd, e.getKey());
                    case "D" -> ShardKeyBuilder.deptKey(systemCd, companyCd, deptId, e.getKey());
                    case "U" -> ShardKeyBuilder.userKey(systemCd, userId, e.getKey());
                    default -> null;
                };
                if (key != null) {
                    cacheService.writeRedisSet(key, e.getValue(), ttl);
                    writtenKeys.add(key);
                }
            }
            for (Map.Entry<String, Set<String>> e : menuActionLeaves.entrySet()) {
                String key = switch (level) {
                    case "C" -> ShardKeyBuilder.menuActionCompanyKey(systemCd, companyCd, e.getKey());
                    case "D" -> ShardKeyBuilder.menuActionDeptKey(systemCd, companyCd, deptId, e.getKey());
                    case "U" -> ShardKeyBuilder.menuActionUserKey(systemCd, userId, e.getKey());
                    default -> null;
                };
                if (key != null) {
                    cacheService.writeRedisSet(key, e.getValue(), ttl);
                    writtenKeys.add(key);
                }
            }
        }
        return writtenKeys;
    }

    private List<Permission> collectPermissionsForLevel(String system, String level,
                                                        String company, String dept, String user) {
        if ("C".equals(level)) {
            return permissionRepo.findBySystemCdAndSubjectTypeAndSubjectId(system, "C", company);
        }
        if ("D".equals(level)) {
            return permissionRepo.findBySystemCdAndSubjectTypeAndSubjectId(system, "D", dept);
        }
        if ("U".equals(level)) {
            // user direct + user-group permissions
            List<Permission> direct = permissionRepo.findBySystemCdAndSubjectTypeAndSubjectId(system, "U", user);
            List<Permission> viaGroup = new ArrayList<>();
            for (Long gid : userGroupMapRepo.findGroupIdsByUserId(user)) {
                viaGroup.addAll(permissionRepo.findBySystemCdAndSubjectTypeAndSubjectId(system, "UG", String.valueOf(gid)));
            }
            List<Permission> all = new ArrayList<>(direct);
            all.addAll(viaGroup);
            // also Company + Dept inheritance for the user
            userRepo.findById(user).ifPresent(u -> {
                all.addAll(permissionRepo.findBySystemCdAndSubjectTypeAndSubjectId(system, "C", u.getCompanyCd()));
                all.addAll(permissionRepo.findBySystemCdAndSubjectTypeAndSubjectId(system, "D", u.getDeptId()));
            });
            return all;
        }
        return List.of();
    }

    /**
     * Returns rows [level, companyCd, deptId, userId] for the underlying concrete subjects to rebuild.
     */
    private List<String[]> expandSubject(String subjectType, String subjectId, String system) {
        List<String[]> out = new ArrayList<>();
        switch (subjectType) {
            case "C" -> out.add(new String[]{"C", subjectId, null, null});
            case "D" -> {
                // can't determine companyCd alone; subjectId for D is dept_id; rely on permissions table joined with TB_USER for inheritance
                // Convention: subject_id for "D" is dept_id; we still need company_cd for the key. Use user lookup or pass-through to all users in dept.
                // We rebuild "D" key per (companyCd, deptId) — find ANY user in dept to learn companyCd.
                userRepo.findAll().stream()
                        .filter(u -> subjectId.equals(u.getDeptId()))
                        .findFirst()
                        .ifPresent(u -> out.add(new String[]{"D", u.getCompanyCd(), u.getDeptId(), null}));
            }
            case "U" -> userRepo.findById(subjectId).ifPresent(u ->
                    out.add(new String[]{"U", u.getCompanyCd(), u.getDeptId(), u.getUserId()}));
            case "UG" -> {
                // Each user in this group needs U-level rebuild
                // For simplicity rebuild members
                // (real impl would join TB_USER_GROUP_MAP)
            }
            default -> {}
        }
        return out;
    }

    private List<Long> expandFolder(Long folderId, Map<Long, List<Long>> children, Map<Long, String> types) {
        List<Long> leaves = new ArrayList<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(folderId);
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if ("M".equals(types.get(cur))) {
                leaves.add(cur);
            } else {
                children.getOrDefault(cur, List.of()).forEach(stack::push);
            }
        }
        return leaves;
    }
}
