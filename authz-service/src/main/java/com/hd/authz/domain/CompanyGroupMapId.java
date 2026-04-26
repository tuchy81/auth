package com.hd.authz.domain;

import lombok.*;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class CompanyGroupMapId implements Serializable {
    private Long companyGroupId;
    private String companyCd;
}
