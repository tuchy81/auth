package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "tb_menu_impl")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuImpl {
    @Id
    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "route_path", length = 500)
    private String routePath;

    @Column(name = "route_name", length = 100)
    private String routeName;

    @Column(name = "component_name", length = 200)
    private String componentName;

    @Column(name = "component_path", length = 500)
    private String componentPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_meta", columnDefinition = "jsonb")
    private Map<String, Object> routeMeta;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_params", columnDefinition = "jsonb")
    private Map<String, Object> routeParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_query", columnDefinition = "jsonb")
    private Map<String, Object> routeQuery;

    @Column(name = "external_url", length = 1000)
    private String externalUrl;

    @Column(name = "open_target", length = 20)
    private String openTarget;

    @Column(name = "has_layout", length = 1)
    private String hasLayout;

    @Column(name = "is_full_screen", length = 1)
    private String isFullScreen;

    @Column(name = "is_modal", length = 1)
    private String isModal;

    @Column(name = "mobile_supported", length = 1)
    private String mobileSupported;

    @Column(name = "mobile_route_path", length = 500)
    private String mobileRoutePath;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
