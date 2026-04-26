package com.hd.authz.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.hd.authz.common.UrlUtils;
import com.hd.authz.domain.SystemShardConfig;
import com.hd.authz.repo.ShardConfigRepo;
import com.hd.authz.repo.UserGroupMapRepo;
import com.hd.authz.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermCacheService {

    private static final Set<String> NEGATIVE = Collections.unmodifiableSet(new HashSet<>());
    private static final long NEG_TTL_SEC = 10L;

    private final Cache<String, Set<String>> permCache;
    private final Cache<String, Object> metaCache;
    private final StringRedisTemplate redis;
    private final ShardConfigRepo shardConfigRepo;
    private final UserRepo userRepo;
    private final UserGroupMapRepo userGroupMapRepo;

    private final AtomicLong l1Hits = new AtomicLong();
    private final AtomicLong l2Hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public SystemShardConfig getShardConfig(String systemCd) {
        return (SystemShardConfig) metaCache.get("shard:" + systemCd, k ->
                shardConfigRepo.findById(systemCd).orElseGet(() -> {
                    SystemShardConfig def = new SystemShardConfig();
                    def.setSystemCd(systemCd);
                    def.setShardStrategy("METHOD_DEPTH");
                    def.setSegmentPosition(1);
                    def.setSegmentMaxLength(32);
                    def.setSegmentFallback("_root");
                    return def;
                })
        );
    }

    /** Spec §7.2 — 3-key (C/D/U) Set membership lookup with L1→L2. */
    public boolean checkApi(String system, String company, String dept, String user,
                            String method, String apiUrl, Long apiId) {
        SystemShardConfig cfg = getShardConfig(system);
        int depth = UrlUtils.depth(apiUrl);
        String seg = ShardKeyBuilder.extractSeg(UrlUtils.normalize(apiUrl), cfg);
        String shard = ShardKeyBuilder.shard(method, depth, seg, cfg);
        String target = String.valueOf(apiId);

        String[] keys = {
                ShardKeyBuilder.companyKey(system, company, shard),
                ShardKeyBuilder.deptKey(system, company, dept, shard),
                ShardKeyBuilder.userKey(system, user, shard)
        };
        for (String k : keys) {
            Set<String> set = lookupSet(k);
            if (set.contains(target)) return true;
        }
        return false;
    }

    public Set<String> lookupSet(String key) {
        Set<String> hit = permCache.getIfPresent(key);
        if (hit != null) {
            l1Hits.incrementAndGet();
            return hit;
        }
        Set<String> mem;
        try {
            mem = redis.opsForSet().members(key);
        } catch (Exception e) {
            log.warn("Redis lookup failed for {}: {}", key, e.toString());
            mem = null;
        }
        if (mem == null || mem.isEmpty()) {
            misses.incrementAndGet();
            permCache.put(key, NEGATIVE);
            return NEGATIVE;
        }
        l2Hits.incrementAndGet();
        permCache.put(key, mem);
        return mem;
    }

    public void invalidateLocal(String key) {
        permCache.invalidate(key);
    }

    public void invalidateAllLocal() {
        permCache.invalidateAll();
    }

    public void writeRedisSet(String key, Set<String> members, Duration ttl) {
        redis.delete(key);
        if (!members.isEmpty()) {
            redis.opsForSet().add(key, members.toArray(new String[0]));
            if (ttl != null) redis.expire(key, ttl);
        }
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("l1_size", permCache.estimatedSize());
        m.put("l1_hits", l1Hits.get());
        m.put("l2_hits", l2Hits.get());
        m.put("misses", misses.get());
        m.put("l1_caffeine_stats", permCache.stats().toString());
        return m;
    }
}
