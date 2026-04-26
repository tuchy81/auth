package com.hd.authz.service;

import com.hd.authz.domain.AuditLog;
import com.hd.authz.domain.Permission;
import com.hd.authz.repo.AuditLogRepo;
import com.hd.authz.repo.MenuRepo;
import com.hd.authz.repo.PermissionRepo;
import com.hd.authz.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * What-if simulation (spec §10.6). NOT persisted — pure read-only diff calculation.
 */
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final PermissionRepo permissionRepo;
    private final MenuRepo menuRepo;
    private final UserRepo userRepo;
    private final AuditLogRepo auditRepo;

    public Map<String, Object> simulateGrant(Map<String, Object> req) {
        String systemCd = (String) req.get("system_cd");
        String subjectType = (String) req.get("subject_type");
        String subjectId = (String) req.get("subject_id");
        Long targetId = ((Number) req.get("target_id")).longValue();
        String actionCd = (String) req.get("action_cd");

        // Find affected leaves (folder expansion)
        var node = menuRepo.findById(targetId).orElseThrow();
        Set<Long> affectedLeaves = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(targetId);
        Map<Long, List<Long>> children = new HashMap<>();
        Map<Long, String> types = new HashMap<>();
        menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc(systemCd).forEach(m -> {
            types.put(m.getMenuId(), m.getMenuType());
            children.computeIfAbsent(m.getParentMenuId(), k -> new ArrayList<>()).add(m.getMenuId());
        });
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if ("M".equals(types.get(cur))) affectedLeaves.add(cur);
            else children.getOrDefault(cur, List.of()).forEach(stack::push);
        }

        // Affected users
        Set<String> affectedUsers = new HashSet<>();
        switch (subjectType) {
            case "C" -> userRepo.findAll().stream()
                    .filter(u -> subjectId.equals(u.getCompanyCd()))
                    .forEach(u -> affectedUsers.add(u.getUserId()));
            case "D" -> userRepo.findAll().stream()
                    .filter(u -> subjectId.equals(u.getDeptId()))
                    .forEach(u -> affectedUsers.add(u.getUserId()));
            case "U" -> affectedUsers.add(subjectId);
            default -> {}
        }

        boolean alreadyGranted = !permissionRepo
                .findBySystemCdAndSubjectTypeAndSubjectId(systemCd, subjectType, subjectId)
                .stream()
                .filter(p -> p.getTargetId().equals(targetId) && p.getActionCd().equals(actionCd))
                .toList().isEmpty();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", "GRANT");
        result.put("already_granted", alreadyGranted);
        result.put("target_menu", Map.of("menu_id", node.getMenuId(),
                "menu_type", node.getMenuType(),
                "menu_nm", node.getMenuNm()));
        result.put("affected_leaf_count", affectedLeaves.size());
        result.put("affected_leaves", affectedLeaves);
        result.put("affected_user_count", affectedUsers.size());
        result.put("subject_type", subjectType);
        result.put("subject_id", subjectId);
        result.put("action_cd", actionCd);

        AuditLog a = new AuditLog();
        a.setActorId((String) req.getOrDefault("actor_id", "system"));
        a.setAction("SIM_RUN");
        a.setSystemCd(systemCd);
        a.setSubjectType(subjectType);
        a.setSubjectId(subjectId);
        a.setTargetType("M");
        a.setTargetId(targetId);
        a.setActionCd(actionCd);
        a.setDetail(result);
        auditRepo.save(a);
        return result;
    }

    public Map<String, Object> simulateRevoke(Map<String, Object> req) {
        Map<String, Object> r = simulateGrant(req);
        r.put("operation", "REVOKE");
        return r;
    }
}
