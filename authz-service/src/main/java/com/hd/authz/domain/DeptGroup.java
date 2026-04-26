package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_dept_group")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeptGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dept_group_id")
    private Long deptGroupId;

    @Column(name = "company_cd", length = 10)
    private String companyCd;

    @Column(name = "group_nm", length = 100)
    private String groupNm;
}
