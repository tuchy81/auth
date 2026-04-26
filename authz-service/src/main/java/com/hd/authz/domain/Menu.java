package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_menu")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "system_cd", length = 20, nullable = false)
    private String systemCd;

    @Column(name = "parent_menu_id")
    private Long parentMenuId;

    @Column(name = "menu_type", length = 1, nullable = false)
    private String menuType; // F/M/L

    @Column(name = "menu_cd", length = 50)
    private String menuCd;

    @Column(name = "menu_nm", length = 200, nullable = false)
    private String menuNm;

    @Column(name = "menu_nm_en", length = 200)
    private String menuNmEn;

    @Column(name = "menu_desc", length = 500)
    private String menuDesc;

    @Column(length = 50)
    private String icon;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "is_visible", length = 1)
    private String isVisible;

    @Column(name = "is_default", length = 1)
    private String isDefault;

    @Column(length = 1)
    private String status;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
