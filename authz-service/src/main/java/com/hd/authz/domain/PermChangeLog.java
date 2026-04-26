package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "tb_perm_change_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PermChangeLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq")
    private Long seq;

    @Column(name = "event_type", length = 30)
    private String eventType;

    @Column(name = "scope_type", length = 2)
    private String scopeType;

    @Column(name = "scope_id", length = 50)
    private String scopeId;

    @Column(name = "system_cd", length = 20)
    private String systemCd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "processed_yn", length = 1)
    private String processedYn;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
