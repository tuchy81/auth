package com.hd.authz.repo;

import com.hd.authz.domain.DeptGroupMap;
import com.hd.authz.domain.DeptGroupMapId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeptGroupMapRepo extends JpaRepository<DeptGroupMap, DeptGroupMapId> {
    List<DeptGroupMap> findByDeptGroupId(Long deptGroupId);
    void deleteByDeptGroupId(Long deptGroupId);

    /** spec §2.7 — DG inheritance: depts in any DG → DG IDs */
    @Query("SELECT m.deptGroupId FROM DeptGroupMap m WHERE m.companyCd = :companyCd AND m.deptId = :deptId")
    List<Long> findGroupIdsByDept(@Param("companyCd") String companyCd, @Param("deptId") String deptId);
}
