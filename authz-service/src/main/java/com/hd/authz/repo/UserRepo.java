package com.hd.authz.repo;

import com.hd.authz.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserRepo extends JpaRepository<UserEntity, String> {
    List<UserEntity> findByCompanyCdAndDeptId(String companyCd, String deptId);
}
