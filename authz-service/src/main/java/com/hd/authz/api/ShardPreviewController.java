package com.hd.authz.api;

import com.hd.authz.domain.SystemShardConfig;
import com.hd.authz.service.ShardPreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/systems")
@RequiredArgsConstructor
public class ShardPreviewController {

    private final ShardPreviewService preview;

    @PostMapping("/{cd}/shard-config/preview")
    public Map<String, Object> previewShard(@PathVariable String cd, @RequestBody Map<String, Object> body) {
        SystemShardConfig cfg = new SystemShardConfig();
        cfg.setSystemCd(cd);
        cfg.setShardStrategy((String) body.getOrDefault("shardStrategy", "METHOD_DEPTH"));
        Object pos = body.get("segmentPosition");
        cfg.setSegmentPosition(pos == null ? 1 : ((Number) pos).intValue());
        Object max = body.get("segmentMaxLength");
        cfg.setSegmentMaxLength(max == null ? 32 : ((Number) max).intValue());
        cfg.setSegmentFallback((String) body.getOrDefault("segmentFallback", "_root"));

        @SuppressWarnings("unchecked")
        List<String> samples = (List<String>) body.getOrDefault("sample_urls", List.of(
                "/api/purchase/requests",
                "/api/purchase/requests/{id}",
                "/api/orders",
                "/health"
        ));
        return preview.preview(cd, cfg, samples);
    }
}
