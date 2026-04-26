package com.hd.authz.domain;

import lombok.*;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class DeptId implements Serializable {
    private String companyCd;
    private String deptId;
}
