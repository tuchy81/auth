package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_user")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserEntity {
    @Id
    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "company_cd", length = 10, nullable = false)
    private String companyCd;

    @Column(name = "dept_id", length = 50, nullable = false)
    private String deptId;

    @Column(name = "user_nm", length = 100)
    private String userNm;

    @Column(length = 200)
    private String email;

    @Column(length = 1)
    private String status;
}
