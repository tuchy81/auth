package com.hd.authz.api;

import com.hd.authz.domain.AuditLog;
import com.hd.authz.domain.Permission;
import com.hd.authz.repo.AuditLogRepo;
import com.hd.authz.repo.MenuActionApiRepo;
import com.hd.authz.repo.PermissionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** spec §10.5 audit/inquiry */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepo auditRepo;
    private final PermissionRepo permRepo;
    private final MenuActionApiRepo maaRepo;

    @GetMapping("/changes")
    public Map<String, Object> changes(@RequestParam(value = "system_cd", required = false) String systemCd,
                                       @RequestParam(value = "subject_id", required = false) String subjectId,
                                       @RequestParam(value = "page", defaultValue = "0") int page,
                                       @RequestParam(value = "size", defaultValue = "50") int size) {
        Page<AuditLog> p = auditRepo.search(systemCd, subjectId, PageRequest.of(page, size));
        return Map.of("content", p.getContent(), "total", p.getTotalElements(),
                      "page", p.getNumber(), "size", p.getSize());
    }

    @GetMapping("/permissions/by-user")
    public List<Permission> byUser(@RequestParam("system_cd") String system,
                                   @RequestParam("user_id") String userId) {
        return permRepo.findBySystemCdAndSubjectTypeAndSubjectId(system, "U", userId);
    }

    @GetMapping("/permissions/by-menu")
    public List<Permission> byMenu(@RequestParam("system_cd") String system,
                                   @RequestParam("menu_id") Long menuId) {
        return permRepo.findBySystemCdAndTargetTypeAndTargetId(system, "M", menuId);
    }

    /** spec §10.5 — by API: which subjects can reach this API */
    @GetMapping("/permissions/by-api")
    public Map<String, Object> byApi(@RequestParam("system_cd") String system,
                                     @RequestParam("api_id") Long apiId) {
        var refs = maaRepo.findAll().stream()
                .filter(m -> apiId.equals(m.getApiId()))
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var ref : refs) {
            permRepo.findAll().stream()
                    .filter(p -> p.getSystemCd().equals(system))
                    .filter(p -> p.getActionCd().equals(ref.getActionCd()))
                    .filter(p -> p.getTargetId().equals(ref.getMenuId()))
                    .forEach(p -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("menu_id", ref.getMenuId());
                        r.put("action_cd", ref.getActionCd());
                        r.put("subject_type", p.getSubjectType());
                        r.put("subject_id", p.getSubjectId());
                        r.put("perm_id", p.getPermId());
                        rows.add(r);
                    });
        }
        return Map.of("api_id", apiId, "menu_action_refs", refs.size(), "subjects", rows);
    }
}
