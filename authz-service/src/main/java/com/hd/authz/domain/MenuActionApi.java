package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_menu_action_api")
@IdClass(MenuActionApiId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuActionApi {
    @Id @Column(name = "menu_id")
    private Long menuId;

    @Id @Column(name = "action_cd", length = 10)
    private String actionCd;

    @Id @Column(name = "api_id")
    private Long apiId;
}
