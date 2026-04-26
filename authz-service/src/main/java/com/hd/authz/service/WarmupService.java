package com.hd.authz.service;

import com.hd.authz.cache.PermCacheService;
import com.hd.authz.cache.ShardKeyBuilder;
import com.hd.authz.domain.UserEntity;
import com.hd.authz.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** spec §9 — Warm-up service: rebuilds and pre-populates L1 with user-scope keys. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarmupService {

    private final PermissionFlattener flattener;
    private final PermCacheService cache;
    private final StringRedisTemplate redis;
    private final UserRepo userRepo;

    public Map<String, Object> warmupSystem(String systemCd, String userId) {
        UserEntity u = userRepo.findById(userId).orElseThrow();

        // 1. Rebuild Redis Set shards for U/D/C levels relevant to this user
        Set<String> userKeys = flattener.rebuildSubject(systemCd, "U", u.getUserId());
        Set<String> deptKeys = flattener.rebuildSubject(systemCd, "D", u.getDeptId());
        Set<String> companyKeys = flattener.rebuildSubject(systemCd, "C", u.getCompanyCd());

        // 2. Pull all keys into Caffeine L1
        int total = 0;
        Set<String> all = new HashSet<>();
        all.addAll(userKeys); all.addAll(deptKeys); all.addAll(companyKeys);
        for (String k : all) {
            Set<String> mem = cache.lookupSet(k);
            total += mem.size();
        }
        // 3. mark warmup state
        redis.opsForValue().set("warmup:user:" + userId + ":" + systemCd, "1",
                java.time.Duration.ofHours(1));

        return Map.of(
                "loaded", true,
                "shard_count", all.size(),
                "total_apis", total
        );
    }

    public Map<String, Object> warmupMenu(String systemCd, String userId, Long menuId, boolean prefetchChildren) {
        UserEntity u = userRepo.findById(userId).orElseThrow();
        // For menu warmup we just rebuild U-level (full) — finer-grain partial warmup is deferred
        Set<String> userKeys = flattener.rebuildSubject(systemCd, "U", u.getUserId());
        for (String k : userKeys) cache.lookupSet(k);
        return Map.of(
                "shard_count", userKeys.size(),
                "menu_id", menuId,
                "prefetched", prefetchChildren
        );
    }
}
