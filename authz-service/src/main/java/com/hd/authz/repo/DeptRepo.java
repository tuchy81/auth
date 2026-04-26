package com.hd.authz.repo;

import com.hd.authz.domain.Dept;
import com.hd.authz.domain.DeptId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeptRepo extends JpaRepository<Dept, DeptId> {
    List<Dept> findByCompanyCd(String companyCd);
}
