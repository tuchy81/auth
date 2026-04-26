package com.hd.authz.repo;

import com.hd.authz.domain.DeptGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeptGroupRepo extends JpaRepository<DeptGroup, Long> {
    List<DeptGroup> findByCompanyCd(String companyCd);
}
