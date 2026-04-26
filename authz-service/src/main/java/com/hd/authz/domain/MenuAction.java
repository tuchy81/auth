package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_menu_action")
@IdClass(MenuActionId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuAction {
    @Id @Column(name = "menu_id")
    private Long menuId;

    @Id @Column(name = "action_cd", length = 10)
    private String actionCd;
}
