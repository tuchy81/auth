package com.hd.authz.domain;

import lombok.*;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class DeptGroupMapId implements Serializable {
    private Long deptGroupId;
    private String companyCd;
    private String deptId;
}
