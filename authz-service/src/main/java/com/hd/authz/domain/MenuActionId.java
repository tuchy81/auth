package com.hd.authz.domain;

import lombok.*;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class MenuActionId implements Serializable {
    private Long menuId;
    private String actionCd;
}
