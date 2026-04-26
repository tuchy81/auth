package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_api")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "api_id")
    private Long apiId;

    @Column(name = "system_cd", length = 20, nullable = false)
    private String systemCd;

    @Column(name = "http_method", length = 10, nullable = false)
    private String httpMethod;

    @Column(name = "url_pattern", length = 500, nullable = false)
    private String urlPattern;

    @Column(name = "url_depth", nullable = false)
    private Integer urlDepth;

    @Column(name = "shard_seg", length = 64)
    private String shardSeg;

    @Column(name = "service_nm", length = 100)
    private String serviceNm;

    @Column(length = 500)
    private String description;

    @Column(length = 1)
    private String status;
}
