package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_permission")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "perm_id")
    private Long permId;

    @Column(name = "system_cd", length = 20, nullable = false)
    private String systemCd;

    @Column(name = "company_cd", length = 10, nullable = false)
    private String companyCd;

    @Column(name = "subject_type", length = 2, nullable = false)
    private String subjectType;     // C/CG/D/DG/U/UG

    @Column(name = "subject_id", length = 50, nullable = false)
    private String subjectId;

    @Column(name = "target_type", length = 1, nullable = false)
    private String targetType;      // M

    @Column(name = "target_id", nullable = false)
    private Long targetId;          // menu_id

    @Column(name = "action_cd", length = 10, nullable = false)
    private String actionCd;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
