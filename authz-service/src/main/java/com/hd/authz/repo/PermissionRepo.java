package com.hd.authz.repo;

import com.hd.authz.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PermissionRepo extends JpaRepository<Permission, Long> {
    List<Permission> findBySystemCdAndSubjectTypeAndSubjectId(String systemCd, String subjectType, String subjectId);
    List<Permission> findBySystemCdAndTargetTypeAndTargetId(String systemCd, String targetType, Long targetId);

    @Query("SELECT p FROM Permission p WHERE p.systemCd=:s AND p.companyCd=:c AND p.subjectType=:st AND p.subjectId=:sid AND p.targetType=:tt AND p.targetId=:tid AND p.actionCd=:ac")
    Optional<Permission> findUnique(@Param("s") String s, @Param("c") String c,
                                    @Param("st") String st, @Param("sid") String sid,
                                    @Param("tt") String tt, @Param("tid") Long tid,
                                    @Param("ac") String ac);

    /** Step 4 — system 별 권한 (audit/stats 용). */
    List<Permission> findBySystemCd(String systemCd);

    /** Step 4 — by-api 추적: (target_id, action_cd) 매칭 */
    @Query("SELECT p FROM Permission p WHERE p.systemCd=:s AND p.targetType='M' AND p.targetId=:menuId AND p.actionCd=:action")
    List<Permission> findByMenuActionInSystem(@Param("s") String s,
                                              @Param("menuId") Long menuId,
                                              @Param("action") String action);
}
