package com.hd.authz.api;

import com.hd.authz.domain.*;
import com.hd.authz.repo.*;
import com.hd.authz.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MasterController {

    private final SystemRepo systemRepo;
    private final ShardConfigRepo shardConfigRepo;
    private final CompanyRepo companyRepo;
    private final DeptRepo deptRepo;
    private final UserRepo userRepo;
    private final MenuRepo menuRepo;
    private final ApiRepo apiRepo;
    private final ActionRepo actionRepo;
    private final PermissionRepo permissionRepo;
    private final PermissionService permissionService;

    // ---- systems ----
    @GetMapping("/systems")
    public List<SystemEntity> listSystems() { return systemRepo.findAll(); }

    @GetMapping("/systems/{cd}")
    public SystemEntity getSystem(@PathVariable String cd) { return systemRepo.findById(cd).orElseThrow(); }

    @PostMapping("/systems")
    public SystemEntity saveSystem(@RequestBody SystemEntity s) { return systemRepo.save(s); }

    @GetMapping("/systems/{cd}/shard-config")
    public SystemShardConfig getShardConfig(@PathVariable String cd) {
        return shardConfigRepo.findById(cd).orElseThrow();
    }

    @PutMapping("/systems/{cd}/shard-config")
    public SystemShardConfig saveShardConfig(@PathVariable String cd, @RequestBody SystemShardConfig cfg) {
        cfg.setSystemCd(cd);
        return shardConfigRepo.save(cfg);
    }

    // ---- org ----
    @GetMapping("/companies")
    public List<Company> listCompanies() { return companyRepo.findAll(); }

    @GetMapping("/companies/{cd}/depts")
    public List<Dept> deptsOf(@PathVariable("cd") String companyCd) { return deptRepo.findByCompanyCd(companyCd); }

    @GetMapping("/users")
    public List<UserEntity> listUsers() { return userRepo.findAll(); }

    @GetMapping("/users/{id}")
    public UserEntity getUser(@PathVariable String id) { return userRepo.findById(id).orElseThrow(); }

    // ---- menu ----
    @GetMapping("/systems/{system}/menus")
    public List<Menu> listMenus(@PathVariable String system) {
        return menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc(system);
    }

    @PostMapping("/menus")
    public Menu saveMenu(@RequestBody Menu m) { return menuRepo.save(m); }

    // ---- api ----
    @GetMapping("/systems/{system}/apis")
    public List<ApiEntity> listApis(@PathVariable String system) {
        return apiRepo.findBySystemCd(system);
    }

    @PostMapping("/apis")
    public ApiEntity saveApi(@RequestBody ApiEntity a) { return apiRepo.save(a); }

    // ---- action ----
    @GetMapping("/systems/{system}/actions")
    public List<Action> listActions(@PathVariable String system) {
        return actionRepo.findBySystemCdOrderBySortOrder(system);
    }

    // ---- permission ----
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
