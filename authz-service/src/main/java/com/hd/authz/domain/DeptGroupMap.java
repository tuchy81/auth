package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_dept_group_map")
@IdClass(DeptGroupMapId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeptGroupMap {
    @Id @Column(name = "dept_group_id")
    private Long deptGroupId;

    @Id @Column(name = "company_cd", length = 10)
    private String companyCd;

    @Id @Column(name = "dept_id", length = 50)
    private String deptId;
}
