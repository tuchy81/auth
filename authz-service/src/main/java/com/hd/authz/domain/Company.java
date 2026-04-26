package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_company")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Company {
    @Id
    @Column(name = "company_cd", length = 10)
    private String companyCd;

    @Column(name = "company_nm", length = 200)
    private String companyNm;
}
