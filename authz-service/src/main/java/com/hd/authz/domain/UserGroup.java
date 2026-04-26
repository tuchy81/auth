package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_user_group")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_group_id")
    private Long userGroupId;

    @Column(name = "company_cd", length = 10)
    private String companyCd;

    @Column(name = "group_nm", length = 100)
    private String groupNm;

    @Column(name = "group_type", length = 20)
    private String groupType;
}
