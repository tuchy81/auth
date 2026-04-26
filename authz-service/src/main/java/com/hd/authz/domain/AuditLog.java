package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "tb_audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "occurred_at", insertable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "actor_id", length = 50)
    private String actorId;

    @Column(length = 40, nullable = false)
    private String action;

    @Column(name = "system_cd", length = 20)
    private String systemCd;

    @Column(name = "subject_type", length = 2)
    private String subjectType;

    @Column(name = "subject_id", length = 50)
    private String subjectId;

    @Column(name = "target_type", length = 1)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "action_cd", length = 10)
    private String actionCd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> detail;
}
