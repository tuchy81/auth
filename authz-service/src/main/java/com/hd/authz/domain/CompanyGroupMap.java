package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_company_group_map")
@IdClass(CompanyGroupMapId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanyGroupMap {
    @Id @Column(name = "company_group_id")
    private Long companyGroupId;

    @Id @Column(name = "company_cd", length = 10)
    private String companyCd;
}
