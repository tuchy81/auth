package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_company_group")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanyGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_group_id")
    private Long companyGroupId;

    @Column(name = "group_nm", length = 100)
    private String groupNm;

    @Column(name = "group_type", length = 20)
    private String groupType;
}
