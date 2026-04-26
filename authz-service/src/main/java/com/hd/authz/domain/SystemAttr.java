package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_system_attr")
@IdClass(SystemAttrId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemAttr {
    @Id @Column(name = "system_cd", length = 20)
    private String systemCd;

    @Id @Column(name = "attr_key", length = 50)
    private String attrKey;

    @Column(name = "attr_value", length = 500)
    private String attrValue;
}
