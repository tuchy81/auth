package com.hd.authz.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_system_shard_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemShardConfig {
    @Id
    @Column(name = "system_cd", length = 20)
    private String systemCd;

    @Column(name = "shard_strategy", length = 20, nullable = false)
    private String shardStrategy;        // METHOD_DEPTH | METHOD_DEPTH_SEG

    @Column(name = "segment_position")
    private Integer segmentPosition;

    @Column(name = "segment_max_length")
    private Integer segmentMaxLength;

    @Column(name = "segment_fallback", length = 50)
    private String segmentFallback;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
