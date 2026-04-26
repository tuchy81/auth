package com.hd.authz.service;

import com.hd.authz.domain.AuditLog;
import com.hd.authz.domain.PermChangeLog;
import com.hd.authz.domain.Permission;
import com.hd.authz.repo.AuditLogRepo;
import com.hd.authz.repo.PermChangeLogRepo;
import com.hd.authz.repo.PermissionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepo permissionRepo;
    private final PermChangeLogRepo changeLogRepo;
    private final AuditLogRepo auditRepo;

    @Transactional
    public Permission grant(Permission p, String actorId) {
        Permission saved = permissionRepo.findUnique(
                p.getSystemCd(), p.getCompanyCd(), p.getSubjectType(), p.getSubjectId(),
                p.getTargetType(), p.getTargetId(), p.getActionCd()
        ).orElseGet(() -> {
            p.setCreatedBy(actorId);
            p.setUpdatedBy(actorId);
            p.setUpdatedAt(LocalDateTime.now());
            return permissionRepo.save(p);
        });
        emit("PERM_GRANT", p.getSystemCd(), p.getSubjectType(), p.getSubjectId(), Map.of(
                "perm_id", saved.getPermId(),
                "target_type", p.getTargetType(),
                "target_id", p.getTargetId(),
                "action_cd", p.getActionCd()));
        audit(actorId, "GRANT", saved);
        return saved;
    }

    @Transactional
    public void revoke(Long permId, String actorId) {
        Permission p = permissionRepo.findById(permId).orElseThrow();
        permissionRepo.deleteById(permId);
        emit("PERM_REVOKE", p.getSystemCd(), p.getSubjectType(), p.getSubjectId(), Map.of(
                "perm_id", permId,
                "target_type", p.getTargetType(),
                "target_id", p.getTargetId(),
                "action_cd", p.getActionCd()));
        audit(actorId, "REVOKE", p);
    }

    public List<Permission> findBySubject(String systemCd, String subjectType, String subjectId) {
        return permissionRepo.findBySystemCdAndSubjectTypeAndSubjectId(systemCd, subjectType, subjectId);
    }

    public List<Permission> findByTarget(String systemCd, String targetType, Long targetId) {
        return permissionRepo.findBySystemCdAndTargetTypeAndTargetId(systemCd, targetType, targetId);
    }

    public void emit(String eventType, String systemCd, String scopeType, String scopeId, Map<String, Object> payload) {
        PermChangeLog log = new PermChangeLog();
        log.setEventType(eventType);
        log.setSystemCd(systemCd);
        log.setScopeType(scopeType);
        log.setScopeId(scopeId);
        log.setPayload(payload);
        log.setProcessedYn("N");
        changeLogRepo.save(log);
    }

    private void audit(String actorId, String action, Permission p) {
        AuditLog a = new AuditLog();
        a.setActorId(actorId);
        a.setAction(action);
        a.setSystemCd(p.getSystemCd());
        a.setSubjectType(p.getSubjectType());
        a.setSubjectId(p.getSubjectId());
        a.setTargetType(p.getTargetType());
        a.setTargetId(p.getTargetId());
        a.setActionCd(p.getActionCd());
        Map<String, Object> det = new HashMap<>();
        det.put("perm_id", p.getPermId());
        det.put("company_cd", p.getCompanyCd());
        a.setDetail(det);
        auditRepo.save(a);
    }
}
