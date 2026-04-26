package com.hd.authz.api;

import com.hd.authz.domain.AuditLog;
import com.hd.authz.domain.Permission;
import com.hd.authz.repo.AuditLogRepo;
import com.hd.authz.repo.PermissionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** spec §10.5 audit/inquiry */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepo auditRepo;
    private final PermissionRepo permRepo;

    /** changes feed */
    @GetMapping("/changes")
    public Map<String, Object> changes(@RequestParam(value = "system_cd", required = false) String systemCd,
                                       @RequestParam(value = "subject_id", required = false) String subjectId,
                                       @RequestParam(value = "page", defaultValue = "0") int page,
                                       @RequestParam(value = "size", defaultValue = "50") int size) {
        Page<AuditLog> p = auditRepo.search(systemCd, subjectId, PageRequest.of(page, size));
        return Map.of(
                "content", p.getContent(),
                "total", p.getTotalElements(),
                "page", p.getNumber(),
                "size", p.getSize()
        );
    }

    /** spec §10.5 — by user */
    @GetMapping("/permissions/by-user")
    public List<Permission> byUser(@RequestParam("system_cd") String system,
                                   @RequestParam("user_id") String userId) {
        // direct + inherited (C/D/UG)
        return permRepo.findBySystemCdAndSubjectTypeAndSubjectId(system, "U", userId);
    }

    /** by menu */
    @GetMapping("/permissions/by-menu")
    public List<Permission> byMenu(@RequestParam("system_cd") String system,
                                   @RequestParam("menu_id") Long menuId) {
        return permRepo.findBySystemCdAndTargetTypeAndTargetId(system, "M", menuId);
    }
}
