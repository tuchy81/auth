package com.hd.authz.api;

import com.hd.authz.domain.*;
import com.hd.authz.repo.*;
import com.hd.authz.service.PermissionService;
import com.hd.authz.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** System / Org / Action / API masters. Menu lifecycle is in MenuController. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MasterController {

    private final SystemRepo systemRepo;
    private final SystemAttrRepo systemAttrRepo;
    private final ShardConfigRepo shardConfigRepo;
    private final CompanyRepo companyRepo;
    private final DeptRepo deptRepo;
    private final UserRepo userRepo;
    private final ActionRepo actionRepo;
    private final ApiRepo apiRepo;
    private final MenuActionApiRepo menuActionApiRepo;
    private final PermissionRepo permissionRepo;
    private final PermissionService permissionService;
    private final StatsService statsService;

    // =====================================================
    //  SYSTEMS  (spec §10.7)
    // =====================================================
    @GetMapping("/systems")
    public List<SystemEntity> listSystems() { return systemRepo.findAll(); }

    @GetMapping("/systems/{cd}")
    public SystemEntity getSystem(@PathVariable String cd) { return systemRepo.findById(cd).orElseThrow(); }

    @PostMapping("/systems")
    public SystemEntity createSystem(@RequestBody SystemEntity s) {
        return systemRepo.save(s);
    }

    @PutMapping("/systems/{cd}")
    public SystemEntity updateSystem(@PathVariable String cd, @RequestBody SystemEntity s) {
        s.setSystemCd(cd);
        return systemRepo.save(s);
    }

    @DeleteMapping("/systems/{cd}")
    @Transactional
    public Map<String, Object> deleteSystem(@PathVariable String cd) {
        systemAttrRepo.deleteBySystemCd(cd);
        shardConfigRepo.findById(cd).ifPresent(shardConfigRepo::delete);
        systemRepo.findById(cd).ifPresent(systemRepo::delete);
        return Map.of("ok", true);
    }

    @GetMapping("/systems/{cd}/stats")
    public Map<String, Object> systemStats(@PathVariable String cd) { return statsService.systemStats(cd); }

    @GetMapping("/systems/{cd}/attrs")
    public List<SystemAttr> listAttrs(@PathVariable String cd) { return systemAttrRepo.findBySystemCd(cd); }

    @PutMapping("/systems/{cd}/attrs/{key}")
    public SystemAttr setAttr(@PathVariable String cd, @PathVariable String key, @RequestBody Map<String, String> body) {
        SystemAttr a = SystemAttr.builder()
                .systemCd(cd).attrKey(key).attrValue(body.get("attr_value")).build();
        return systemAttrRepo.save(a);
    }

    @DeleteMapping("/systems/{cd}/attrs/{key}")
    public Map<String, Object> deleteAttr(@PathVariable String cd, @PathVariable String key) {
        SystemAttrId id = new SystemAttrId(); id.setSystemCd(cd); id.setAttrKey(key);
        systemAttrRepo.deleteById(id);
        return Map.of("ok", true);
    }

    @GetMapping("/systems/{cd}/shard-config")
    public SystemShardConfig getShardConfig(@PathVariable String cd) {
        return shardConfigRepo.findById(cd).orElseThrow();
    }

    @PutMapping("/systems/{cd}/shard-config")
    public SystemShardConfig saveShardConfig(@PathVariable String cd, @RequestBody SystemShardConfig cfg) {
        cfg.setSystemCd(cd);
        SystemShardConfig saved = shardConfigRepo.save(cfg);
        permissionService.emit("SHARD_STRATEGY_CHANGE", cd, null, null,
                Map.of("strategy", cfg.getShardStrategy(),
                       "segment_position", String.valueOf(cfg.getSegmentPosition())));
        return saved;
    }

    // =====================================================
    //  ORG: company / dept / user
    // =====================================================
    @GetMapping("/companies")
    public List<Company> listCompanies() { return companyRepo.findAll(); }

    @PostMapping("/companies")
    public Company saveCompany(@RequestBody Company c) { return companyRepo.save(c); }

    @PutMapping("/companies/{cd}")
    public Company updateCompany(@PathVariable String cd, @RequestBody Company c) {
        c.setCompanyCd(cd); return companyRepo.save(c);
    }

    @DeleteMapping("/companies/{cd}")
    public Map<String, Object> deleteCompany(@PathVariable String cd) {
        companyRepo.deleteById(cd); return Map.of("ok", true);
    }

    @GetMapping("/companies/{cd}/depts")
    public List<Dept> deptsOf(@PathVariable("cd") String cd) { return deptRepo.findByCompanyCd(cd); }

    @PostMapping("/depts")
    public Dept saveDept(@RequestBody Dept d) { return deptRepo.save(d); }

    @DeleteMapping("/depts/{companyCd}/{deptId}")
    public Map<String, Object> deleteDept(@PathVariable String companyCd, @PathVariable String deptId) {
        DeptId id = new DeptId(); id.setCompanyCd(companyCd); id.setDeptId(deptId);
        deptRepo.deleteById(id);
        return Map.of("ok", true);
    }

    @GetMapping("/users")
    public List<UserEntity> listUsers() { return userRepo.findAll(); }

    @GetMapping("/users/{id}")
    public UserEntity getUser(@PathVariable String id) { return userRepo.findById(id).orElseThrow(); }

    @PostMapping("/users")
    public UserEntity saveUser(@RequestBody UserEntity u) { return userRepo.save(u); }

    @PutMapping("/users/{id}")
    public UserEntity updateUser(@PathVariable String id, @RequestBody UserEntity u) {
        u.setUserId(id); return userRepo.save(u);
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable String id) {
        userRepo.deleteById(id); return Map.of("ok", true);
    }

    // =====================================================
    //  ACTIONS
    // =====================================================
    @GetMapping("/systems/{system}/actions")
    public List<Action> listActions(@PathVariable String system) {
        return actionRepo.findBySystemCdOrderBySortOrder(system);
    }

    @PostMapping("/systems/{system}/actions")
    public Action saveAction(@PathVariable String system, @RequestBody Action a) {
        a.setSystemCd(system); return actionRepo.save(a);
    }

    @PutMapping("/systems/{system}/actions/{cd}")
    public Action updateAction(@PathVariable String system, @PathVariable String cd, @RequestBody Action a) {
        a.setSystemCd(system); a.setActionCd(cd); return actionRepo.save(a);
    }

    @DeleteMapping("/systems/{system}/actions/{cd}")
    public Map<String, Object> deleteAction(@PathVariable String system, @PathVariable String cd) {
        ActionId id = new ActionId(); id.setSystemCd(system); id.setActionCd(cd);
        actionRepo.deleteById(id);
        return Map.of("ok", true);
    }

    // =====================================================
    //  API masters
    // =====================================================
    @GetMapping("/systems/{system}/apis")
    public List<ApiEntity> listApis(@PathVariable String system) { return apiRepo.findBySystemCd(system); }

    @PostMapping("/apis")
    public ApiEntity createApi(@RequestBody ApiEntity a) {
        if (a.getUrlDepth() == null) {
            a.setUrlDepth(com.hd.authz.common.UrlUtils.depth(a.getUrlPattern()));
        }
        return apiRepo.save(a);
    }

    @PutMapping("/apis/{id}")
    public ApiEntity updateApi(@PathVariable Long id, @RequestBody ApiEntity a) {
        a.setApiId(id);
        if (a.getUrlDepth() == null) {
            a.setUrlDepth(com.hd.authz.common.UrlUtils.depth(a.getUrlPattern()));
        }
        return apiRepo.save(a);
    }

    @DeleteMapping("/apis/{id}")
    @Transactional
    public Map<String, Object> deleteApi(@PathVariable Long id) {
        // Step 4 — N+1 제거
        menuActionApiRepo.findByApiId(id).forEach(menuActionApiRepo::delete);
        apiRepo.deleteById(id);
        return Map.of("ok", true);
    }

    @GetMapping("/apis/{id}/usages")
    public List<Map<String, Object>> apiUsages(@PathVariable Long id) {
        return menuActionApiRepo.findByApiId(id).stream()
                .map(m -> (Map<String, Object>) Map.<String, Object>of(
                        "menu_id", m.getMenuId(), "action_cd", m.getActionCd()))
                .toList();
    }

    /** Step 4 — bulk: ApisView 1000번 호출 → 1번 (반환: 매핑된 api_id Set) */
    @GetMapping("/systems/{system}/api-mapped-set")
    public java.util.Set<Long> apiMappedSet(@PathVariable String system) {
        return menuActionApiRepo.findMappedApiIdsForSystem(system);
    }

    // =====================================================
    //  PERMISSIONS  (CRUD shared)
    // =====================================================
    @GetMapping("/permissions/by-subject")
    public List<Permission> bySubject(@RequestParam("system_cd") String system,
                                      @RequestParam("subject_type") String type,
                                      @RequestParam("subject_id") String id) {
        return permissionService.findBySubject(system, type, id);
    }

    @GetMapping("/permissions/by-target")
    public List<Permission> byTarget(@RequestParam("system_cd") String system,
                                     @RequestParam("target_type") String type,
                                     @RequestParam("target_id") Long id) {
        return permissionService.findByTarget(system, type, id);
    }

    @PostMapping("/permissions")
    public Permission grant(@RequestBody Permission p,
                            @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
        return permissionService.grant(p, actor);
    }

    @DeleteMapping("/permissions/{id}")
    public Map<String, Object> revoke(@PathVariable("id") Long id,
                                      @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
        permissionService.revoke(id, actor);
        return Map.of("ok", true);
    }
}
