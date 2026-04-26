package com.hd.authz.service;

import com.hd.authz.cache.ShardKeyBuilder;
import com.hd.authz.common.UrlUtils;
import com.hd.authz.domain.ApiEntity;
import com.hd.authz.domain.SystemShardConfig;
import com.hd.authz.repo.ApiRepo;
import com.hd.authz.repo.ShardConfigRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** Spec §10.10 — preview shard config change impact. */
@Service
@RequiredArgsConstructor
public class ShardPreviewService {

    private final ShardConfigRepo shardConfigRepo;
    private final ApiRepo apiRepo;
    private final StringRedisTemplate redis;

    public Map<String, Object> preview(String systemCd, SystemShardConfig newCfg, List<String> sampleUrls) {
        SystemShardConfig current = shardConfigRepo.findById(systemCd).orElse(null);

        // sample preview: how each URL gets sharded
        List<Map<String, Object>> samples = new ArrayList<>();
        for (String url : sampleUrls) {
            String seg = ShardKeyBuilder.extractSeg(url, newCfg);
            int depth = UrlUtils.depth(url);
            String shard = ShardKeyBuilder.shard("GET", depth, seg, newCfg);
            samples.add(Map.of(
                    "url", url,
                    "depth", depth,
                    "seg", seg == null ? "(none)" : seg,
                    "shard", shard
            ));
        }

        // impact: total apis × distinct shards (under each strategy)
        List<ApiEntity> apis = apiRepo.findBySystemCd(systemCd);
        Set<String> currentShards = new HashSet<>();
        Set<String> newShards = new HashSet<>();
        int segFallbackCount = 0;
        for (ApiEntity a : apis) {
            String currentSeg = current != null ? ShardKeyBuilder.extractSeg(a.getUrlPattern(), current) : null;
            String newSeg = ShardKeyBuilder.extractSeg(a.getUrlPattern(), newCfg);
            currentShards.add(ShardKeyBuilder.shard(a.getHttpMethod(), a.getUrlDepth(), currentSeg, current));
            newShards.add(ShardKeyBuilder.shard(a.getHttpMethod(), a.getUrlDepth(), newSeg, newCfg));
            if (newSeg != null && newSeg.equals(newCfg.getSegmentFallback())) segFallbackCount++;
        }
        Set<String> currentKeys = redis.keys("perm:*:" + systemCd + ":*");
        long currentKeyCount = currentKeys == null ? 0 : currentKeys.size();
        // estimate: rebuild scaled by ratio (newShards/currentShards)
        double ratio = currentShards.isEmpty() ? 1.0 : (double) newShards.size() / currentShards.size();
        long newKeyEstimate = Math.round(currentKeyCount * ratio);

        // estimated rebuild time: ~1ms per key
        long estMs = newKeyEstimate * 2;

        return Map.of(
                "current_strategy", current != null ? current.getShardStrategy() : "(none)",
                "new_strategy", newCfg.getShardStrategy(),
                "current_distinct_shards", currentShards.size(),
                "new_distinct_shards", newShards.size(),
                "current_key_count", currentKeyCount,
                "new_key_estimate", newKeyEstimate,
                "seg_fallback_count", segFallbackCount,
                "seg_fallback_ratio_pct", apis.isEmpty() ? 0 : Math.round(segFallbackCount * 100.0 / apis.size()),
                "rebuild_est_ms", estMs,
                "samples", samples
        );
    }
}
