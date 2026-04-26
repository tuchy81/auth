package com.hd.authz.api;

import com.hd.authz.domain.*;
import com.hd.authz.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Group CRUD per spec §5.7 / §10.4 — Company / Dept / User groups + members.
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final CompanyGroupRepo companyGroupRepo;
    private final CompanyGroupMapRepo companyGroupMapRepo;
    private final DeptGroupRepo deptGroupRepo;
    private final DeptGroupMapRepo deptGroupMapRepo;
    private final UserGroupRepo userGroupRepo;
    private final UserGroupMapRepo userGroupMapRepo;

    // ===== Company Groups (CG) =====
    @GetMapping("/company")
    public List<CompanyGroup> listCG() { return companyGroupRepo.findAll(); }

    @PostMapping("/company")
    public CompanyGroup saveCG(@RequestBody CompanyGroup g) { return companyGroupRepo.save(g); }

    @PutMapping("/company/{id}")
    public CompanyGroup updateCG(@PathVariable Long id, @RequestBody CompanyGroup g) {
        g.setCompanyGroupId(id); return companyGroupRepo.save(g);
    }

    @DeleteMapping("/company/{id}")
    @Transactional
    public Map<String, Object> deleteCG(@PathVariable Long id) {
        companyGroupMapRepo.deleteByCompanyGroupId(id);
        companyGroupRepo.deleteById(id);
        return Map.of("ok", true);
    }

    @GetMapping("/company/{id}/members")
    public List<CompanyGroupMap> cgMembers(@PathVariable Long id) {
        return companyGroupMapRepo.findByCompanyGroupId(id);
    }

    @PostMapping("/company/{id}/members/{companyCd}")
    public Map<String, Object> addCGMember(@PathVariable Long id, @PathVariable String companyCd) {
        companyGroupMapRepo.save(CompanyGroupMap.builder().companyGroupId(id).companyCd(companyCd).build());
        return Map.of("ok", true);
    }

    @DeleteMapping("/company/{id}/members/{companyCd}")
    public Map<String, Object> removeCGMember(@PathVariable Long id, @PathVariable String companyCd) {
        CompanyGroupMapId mid = new CompanyGroupMapId();
        mid.setCompanyGroupId(id); mid.setCompanyCd(companyCd);
        companyGroupMapRepo.deleteById(mid);
        return Map.of("ok", true);
    }

    // ===== Dept Groups (DG) =====
    @GetMapping("/dept")
    public List<DeptGroup> listDG(@RequestParam(value = "company_cd", required = false) String company) {
        return company == null ? deptGroupRepo.findAll() : deptGroupRepo.findByCompanyCd(company);
    }

    @PostMapping("/dept")
    public DeptGroup saveDG(@RequestBody DeptGroup g) { return deptGroupRepo.save(g); }

    @PutMapping("/dept/{id}")
    public DeptGroup updateDG(@PathVariable Long id, @RequestBody DeptGroup g) {
        g.setDeptGroupId(id); return deptGroupRepo.save(g);
    }

    @DeleteMapping("/dept/{id}")
    @Transactional
    public Map<String, Object> deleteDG(@PathVariable Long id) {
        deptGroupMapRepo.deleteByDeptGroupId(id);
        deptGroupRepo.deleteById(id);
        return Map.of("ok", true);
    }

    @GetMapping("/dept/{id}/members")
    public List<DeptGroupMap> dgMembers(@PathVariable Long id) {
        return deptGroupMapRepo.findByDeptGroupId(id);
    }

    @PostMapping("/dept/{id}/members")
    public Map<String, Object> addDGMember(@PathVariable Long id, @RequestBody Map<String, String> body) {
        deptGroupMapRepo.save(DeptGroupMap.builder()
                .deptGroupId(id).companyCd(body.get("companyCd")).deptId(body.get("deptId")).build());
        return Map.of("ok", true);
    }

    @DeleteMapping("/dept/{id}/members/{companyCd}/{deptId}")
    public Map<String, Object> removeDGMember(@PathVariable Long id, @PathVariable String companyCd, @PathVariable String deptId) {
        DeptGroupMapId mid = new DeptGroupMapId();
        mid.setDeptGroupId(id); mid.setCompanyCd(companyCd); mid.setDeptId(deptId);
        deptGroupMapRepo.deleteById(mid);
        return Map.of("ok", true);
    }

    // ===== User Groups (UG) =====
    @GetMapping("/user")
    public List<UserGroup> listUG(@RequestParam(value = "company_cd", required = false) String company) {
        return company == null ? userGroupRepo.findAll() : userGroupRepo.findByCompanyCd(company);
    }

    @PostMapping("/user")
    public UserGroup saveUG(@RequestBody UserGroup g) { return userGroupRepo.save(g); }

    @PutMapping("/user/{id}")
    public UserGroup updateUG(@PathVariable Long id, @RequestBody UserGroup g) {
        g.setUserGroupId(id); return userGroupRepo.save(g);
    }

    @DeleteMapping("/user/{id}")
    @Transactional
    public Map<String, Object> deleteUG(@PathVariable Long id) {
        userGroupMapRepo.findGroupIdsByUserId(""); // no-op to keep import
        userGroupMapRepo.findAll().stream()
                .filter(m -> m.getUserGroupId().equals(id))
                .forEach(userGroupMapRepo::delete);
        userGroupRepo.deleteById(id);
        return Map.of("ok", true);
    }

    @GetMapping("/user/{id}/members")
    public List<UserGroupMap> ugMembers(@PathVariable Long id) {
        return userGroupMapRepo.findAll().stream()
                .filter(m -> m.getUserGroupId().equals(id))
                .toList();
    }

    @PostMapping("/user/{id}/members/{userId}")
    public Map<String, Object> addUGMember(@PathVariable Long id, @PathVariable String userId) {
        userGroupMapRepo.save(UserGroupMap.builder().userGroupId(id).userId(userId).build());
        return Map.of("ok", true);
    }

    @DeleteMapping("/user/{id}/members/{userId}")
    public Map<String, Object> removeUGMember(@PathVariable Long id, @PathVariable String userId) {
        UserGroupMapId mid = new UserGroupMapId();
        mid.setUserGroupId(id); mid.setUserId(userId);
        userGroupMapRepo.deleteById(mid);
        return Map.of("ok", true);
    }

    @PostMapping("/user/{id}/members/csv")
    public Map<String, Object> importUGMembersCsv(@PathVariable Long id, @RequestBody String csv) {
        int added = 0, skipped = 0;
        for (String line : csv.split("\\r?\\n")) {
            String uid = line.trim();
            if (uid.isEmpty() || uid.startsWith("#")) continue;
            UserGroupMapId mid = new UserGroupMapId(); mid.setUserGroupId(id); mid.setUserId(uid);
            if (userGroupMapRepo.existsById(mid)) { skipped++; continue; }
            userGroupMapRepo.save(UserGroupMap.builder().userGroupId(id).userId(uid).build());
            added++;
        }
        return Map.of("added", added, "skipped", skipped);
    }
}
