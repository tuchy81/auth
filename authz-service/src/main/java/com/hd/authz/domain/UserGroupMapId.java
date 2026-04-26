package com.hd.authz.domain;

import lombok.*;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class UserGroupMapId implements Serializable {
    private Long userGroupId;
    private String userId;
}
