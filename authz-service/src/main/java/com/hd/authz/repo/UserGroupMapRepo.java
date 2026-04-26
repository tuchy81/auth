package com.hd.authz.repo;

import com.hd.authz.domain.UserGroupMap;
import com.hd.authz.domain.UserGroupMapId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserGroupMapRepo extends JpaRepository<UserGroupMap, UserGroupMapId> {
    @Query("SELECT m.userGroupId FROM UserGroupMap m WHERE m.userId = :userId")
    List<Long> findGroupIdsByUserId(@Param("userId") String userId);

    @Query("SELECT m FROM UserGroupMap m WHERE m.userGroupId = :groupId")
    List<UserGroupMap> findMembersByGroupId(@Param("groupId") Long groupId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM UserGroupMap m WHERE m.userGroupId = :groupId")
    void deleteAllByUserGroupId(@Param("groupId") Long groupId);
}
