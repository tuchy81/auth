package com.hd.authz.domain;

import lombok.*;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class SystemAttrId implements Serializable {
    private String systemCd;
    private String attrKey;
}
