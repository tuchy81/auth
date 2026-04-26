package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_dept")
@IdClass(DeptId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Dept {
    @Id
    @Column(name = "company_cd", length = 10)
    private String companyCd;

    @Id
    @Column(name = "dept_id", length = 50)
    private String deptId;

    @Column(name = "dept_cd", length = 50)
    private String deptCd;

    @Column(name = "dept_nm", length = 200)
    private String deptNm;
}
