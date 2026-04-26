package com.hd.authz.repo;

import com.hd.authz.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AuditLogRepo extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findBySystemCdAndSubjectTypeAndSubjectIdOrderByOccurredAtDesc(
            String systemCd, String subjectType, String subjectId);

    @Query("SELECT a FROM AuditLog a WHERE (:systemCd IS NULL OR a.systemCd=:systemCd) " +
           "AND (:subjectId IS NULL OR a.subjectId=:subjectId) " +
           "ORDER BY a.occurredAt DESC")
    Page<AuditLog> search(@Param("systemCd") String systemCd,
                          @Param("subjectId") String subjectId,
                          Pageable pageable);
}
