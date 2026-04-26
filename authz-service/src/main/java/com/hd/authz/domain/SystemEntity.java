package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_system")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemEntity {
    @Id
    @Column(name = "system_cd", length = 20)
    private String systemCd;

    @Column(name = "system_nm", length = 100, nullable = false)
    private String systemNm;

    @Column(name = "system_nm_en", length = 100)
    private String systemNmEn;

    @Column(length = 500)
    private String description;

    @Column(name = "owner_company_cd", length = 10)
    private String ownerCompanyCd;

    @Column(name = "owner_division", length = 50)
    private String ownerDivision;

    @Column(name = "owner_dept_id", length = 50)
    private String ownerDeptId;

    @Column(name = "owner_user_id", length = 50)
    private String ownerUserId;

    @Column(name = "system_type", length = 20)
    private String systemType;

    @Column(name = "system_category", length = 50)
    private String systemCategory;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "frontend_type", length = 20)
    private String frontendType;

    @Column(length = 1)
    private String status;

    @Column(name = "go_live_date")
    private LocalDate goLiveDate;

    @Column(name = "end_of_life_date")
    private LocalDate endOfLifeDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
