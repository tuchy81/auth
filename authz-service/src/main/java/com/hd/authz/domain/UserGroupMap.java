package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_user_group_map")
@IdClass(UserGroupMapId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserGroupMap {
    @Id @Column(name = "user_group_id")
    private Long userGroupId;

    @Id @Column(name = "user_id", length = 50)
    private String userId;
}
