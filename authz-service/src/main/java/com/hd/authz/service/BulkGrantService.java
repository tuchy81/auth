package com.hd.authz.service;

import com.hd.authz.domain.Menu;
import com.hd.authz.domain.MenuActionApi;
import com.hd.authz.domain.Permission;
import com.hd.authz.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Spec §10.3 — bulk grant with pre-impact analysis. */
@Service
@RequiredArgsConstructor
public class BulkGrantService {

    private final MenuRepo menuRepo;
    private final UserRepo userRepo;
    private final MenuActionApiRepo menuActionApiRepo;
    private final PermissionRepo permissionRepo;
    private final PermissionService permissionService;
    private final UserGroupMapRepo userGroupMapRepo;

    /** Calculate impact for a bulk grant request without persisting. */
    public Map<String, Object> previewBulk(BulkGrantReq req) {
        String systemCd = req.systemCd;
        Map<Long, List<Long>> children = new HashMap<>();
        Map<Long, String> types = new HashMap<>();
        menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc(systemCd).forEach(m -> {
            types.put(m.getMenuId(), m.getMenuType());
            children.computeIfAbsent(m.getParentMenuId(), k -> new ArrayList<>()).add(m.getMenuId());
        });

        // expand all selected target menus to leaves
        Set<Long> leaves = new HashSet<>();
        for (Long mid : req.targetMenuIds) {
            String type = types.get(mid);
            if ("F".equals(type)) leaves.addAll(expandFolder(mid, children, types));
            else if ("M".equals(type)) leaves.add(mid);
        }

        // affected users
        Set<String> affectedUsers = expandUsers(req.subjectType, req.subjectId);

        // grant count = |leaves| * |actions|
        long grantCount = (long) leaves.size() * req.actions.size();

        // new APIs that will be allowed (rough estimate: distinct api_ids on leaf×action)
        Set<Long> newApiIds = new HashSet<>();
        for (Long leaf : leaves) {
            for (String act : req.actions) {
                menuActionApiRepo.findByMenuIdAndActionCd(leaf, act)
                        .stream().map(MenuActionApi::getApiId)
                        .forEach(newApiIds::add);
            }
        }

        // shard rebuild estimate (per-user)
        long shardKeys = (long) affectedUsers.size() * 12; // ~12 shards / user typical
        long estMs = Math.max(500, shardKeys * 2);

        return Map.of(
                "leaf_count", leaves.size(),
                "leaf_ids", leaves,
                "action_count", req.actions.size(),
                "grant_count", grantCount,
                "affected_users", affectedUsers.size(),
                "new_api_count", newApiIds.size(),
                "cache_rebuild_keys", shardKeys,
                "est_ms", estMs,
                "subject_type", req.subjectType,
                "subject_id", req.subjectId
        );
    }

    @Transactional
    public Map<String, Object> applyBulk(BulkGrantReq req, String actor) {
        Map<Long, List<Long>> children = new HashMap<>();
        Map<Long, String> types = new HashMap<>();
        menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc(req.systemCd).forEach(m -> {
            types.put(m.getMenuId(), m.getMenuType());
            children.computeIfAbsent(m.getParentMenuId(), k -> new ArrayList<>()).add(m.getMenuId());
        });
        // Spec note: we grant on the SELECTED menu (folder permitted) — flatten happens on cache.
        // But we still need per-(menu,action) row, dedup if exists.
        int created = 0, skipped = 0;
        String companyCd = resolveCompanyCd(req);
        for (Long mid : req.targetMenuIds) {
            for (String action : req.actions) {
                Optional<Permission> existing = permissionRepo.findUnique(
                        req.systemCd, companyCd, req.subjectType, req.subjectId, "M", mid, action);
                if (existing.isPresent()) { skipped++; continue; }
                Permission p = Permission.builder()
                        .systemCd(req.systemCd).companyCd(companyCd)
                        .subjectType(req.subjectType).subjectId(req.subjectId)
                        .targetType("M").targetId(mid).actionCd(action)
                        .validFrom(req.validFrom).validTo(req.validTo)
                        .createdBy(actor).updatedBy(actor)
                        .build();
                permissionService.grant(p, actor);
                created++;
            }
        }
        return Map.of("created", created, "skipped", skipped);
    }

    private String resolveCompanyCd(BulkGrantReq req) {
        if ("C".equals(req.subjectType) || "CG".equals(req.subjectType)) return req.subjectId;
        if ("U".equals(req.subjectType)) {
            return userRepo.findById(req.subjectId).map(u -> u.getCompanyCd())
                    .orElse(req.companyCd != null ? req.companyCd : "_");
        }
        return req.companyCd != null ? req.companyCd : "_";
    }

    private Set<String> expandUsers(String subjectType, String subjectId) {
        Set<String> out = new HashSet<>();
        switch (subjectType) {
            case "C" -> userRepo.findAll().stream().filter(u -> subjectId.equals(u.getCompanyCd())).forEach(u -> out.add(u.getUserId()));
            case "D" -> userRepo.findAll().stream().filter(u -> subjectId.equals(u.getDeptId())).forEach(u -> out.add(u.getUserId()));
            case "U" -> out.add(subjectId);
            case "UG" -> {
                long gid;
                try { gid = Long.parseLong(subjectId); } catch (Exception e) { return out; }
                userGroupMapRepo.findAll().stream()
                        .filter(m -> Objects.equals(gid, m.getUserGroupId()))
                        .forEach(m -> out.add(m.getUserId()));
            }
            default -> {}
        }
        return out;
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

    /** Request body. */
    public static class BulkGrantReq {
        public String systemCd;
        public String companyCd;
        public String subjectType;          // C/D/U/CG/DG/UG
        public String subjectId;
        public List<Long> targetMenuIds;    // folder OK
        public List<String> actions;
        public java.time.LocalDateTime validFrom;
        public java.time.LocalDateTime validTo;
    }
}
