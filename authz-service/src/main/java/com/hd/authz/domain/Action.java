package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_action")
@IdClass(ActionId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Action {
    @Id @Column(name = "system_cd", length = 20)
    private String systemCd;

    @Id @Column(name = "action_cd", length = 10)
    private String actionCd;

    @Column(name = "action_nm", length = 50)
    private String actionNm;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
