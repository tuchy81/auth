package com.hd.authz.repo;

import com.hd.authz.domain.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserGroupRepo extends JpaRepository<UserGroup, Long> {
    List<UserGroup> findByCompanyCd(String companyCd);
}
