package com.hd.authz.repo;

import com.hd.authz.domain.DeptGroupMap;
import com.hd.authz.domain.DeptGroupMapId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeptGroupMapRepo extends JpaRepository<DeptGroupMap, DeptGroupMapId> {
    List<DeptGroupMap> findByDeptGroupId(Long deptGroupId);
    void deleteByDeptGroupId(Long deptGroupId);
}
