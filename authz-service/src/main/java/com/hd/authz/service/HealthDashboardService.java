package com.hd.authz.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.hd.authz.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 전체 서비스 + 구성요소 헬스 대시보드 데이터 집계.
 * 스펙 §11.5 모니터링 지표 + 운영 SLA 일관 점검.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthDashboardService {

    @Autowired(required = false) private HealthEndpoint healthEndpoint;
    private final StringRedisTemplate redis;
    private final RedisConnectionFactory redisCf;

    private final Cache<String, Set<String>> permCache;

    private final SystemRepo systemRepo;
    private final ApiRepo apiRepo;
    private final MenuRepo menuRepo;
    private final UserRepo userRepo;
    private final PermissionRepo permissionRepo;
    private final PermChangeLogRepo logRepo;
    private final MenuActionApiRepo menuActionApiRepo;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("timestamp", LocalDateTime.now().toString());
        out.put("services", services());
        out.put("database", database());
        out.put("redis", redisStats());
        out.put("cache_l1", caffeineStats());
        out.put("sync_worker", syncWorker());
        out.put("data", dataCounts());
        out.put("systems", perSystemStats());
        return out;
    }

    /** 외부 서비스 health 점검 (HTTP). */
    private Map<String, Object> services() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(probeHttp("authz-service", "http://localhost:8080/actuator/health"));
        list.add(probeHttp("sample-api",    "http://localhost:8081/actuator/health"));
        list.add(probeHttp("authz-admin",   "http://localhost:5173/"));
        list.add(probeHttp("pgadmin",       "http://localhost:5050/login"));
        return Map.of("list", list);
    }

    private Map<String, Object> probeHttp(String name, String url) {
        long t0 = System.nanoTime();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", name);
        r.put("url", url);
        try {
            HttpResponse<Void> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            int code = resp.statusCode();
            // 401 (pgadmin login required) 도 살아있는 것으로 간주
            r.put("status", (code >= 200 && code < 400) || code == 401 ? "UP" : "DOWN");
            r.put("http_code", code);
        } catch (Exception e) {
            r.put("status", "DOWN");
            r.put("http_code", 0);
            r.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        r.put("latency_ms", (System.nanoTime() - t0) / 1_000_000);
        return r;
    }

    /** PostgreSQL 통계: 테이블 count + connection healthy */
    private Map<String, Object> database() {
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            r.put("status", healthEndpoint != null
                    ? String.valueOf(healthEndpoint.healthForPath("db").getStatus())
                    : "UNKNOWN");
        } catch (Exception e) {
            r.put("status", "UNKNOWN");
        }
        r.put("tb_system",       safeCount(systemRepo::count));
        r.put("tb_api",          safeCount(apiRepo::count));
        r.put("tb_menu",         safeCount(menuRepo::count));
        r.put("tb_user",         safeCount(userRepo::count));
        r.put("tb_permission",   safeCount(permissionRepo::count));
        r.put("tb_change_log",   safeCount(logRepo::count));
        r.put("tb_menu_action_api", safeCount(menuActionApiRepo::count));
        return r;
    }

    /** Redis stats: keys + hit ratio + INFO */
    private Map<String, Object> redisStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            String pong = redisCf.getConnection().ping();
            r.put("status", "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN");
            Set<String> permKeys = redis.keys("perm:*");
            Set<String> warmupKeys = redis.keys("warmup:*");
            r.put("perm_keys", permKeys == null ? 0 : permKeys.size());
            r.put("warmup_keys", warmupKeys == null ? 0 : warmupKeys.size());

            Properties info = redisCf.getConnection().serverCommands().info("stats");
            if (info != null) {
                long hits = parseLong(info.getProperty("keyspace_hits"));
                long misses = parseLong(info.getProperty("keyspace_misses"));
                double hitRatio = (hits + misses) == 0 ? 0 : (double) hits / (hits + misses) * 100;
                r.put("keyspace_hits", hits);
                r.put("keyspace_misses", misses);
                r.put("hit_ratio_pct", Math.round(hitRatio * 100) / 100.0);
            }
            Properties mem = redisCf.getConnection().serverCommands().info("memory");
            if (mem != null) {
                r.put("used_memory_mb", parseLong(mem.getProperty("used_memory")) / 1_048_576);
            }
        } catch (Exception e) {
            r.put("status", "DOWN");
            r.put("error", e.getMessage());
        }
        return r;
    }

    /** Caffeine L1 stats */
    private Map<String, Object> caffeineStats() {
        var s = permCache.stats();
        long total = s.requestCount();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("estimated_size", permCache.estimatedSize());
        r.put("hit_count", s.hitCount());
        r.put("miss_count", s.missCount());
        r.put("hit_ratio_pct", total == 0 ? 0 : Math.round(s.hitRate() * 10000) / 100.0);
        r.put("eviction_count", s.evictionCount());
        r.put("load_failures", s.loadFailureCount());
        return r;
    }

    /** Sync Worker lag (스펙 §11.5) */
    private Map<String, Object> syncWorker() {
        Map<String, Object> r = new LinkedHashMap<>();
        long unprocessed = logRepo.findAll().stream()
                .filter(l -> "N".equals(l.getProcessedYn()))
                .count();
        r.put("unprocessed_events", unprocessed);
        r.put("alert", unprocessed > 100 ? "⚠ 100건 초과 — sync worker 점검 필요" : "OK");

        // recent events
        var recent = logRepo.findAll().stream()
                .sorted((a, b) -> Long.compare(b.getSeq(), a.getSeq()))
                .limit(5)
                .map(l -> Map.of(
                        "seq", l.getSeq(),
                        "type", l.getEventType() == null ? "?" : l.getEventType(),
                        "processed", l.getProcessedYn(),
                        "created_at", l.getCreatedAt() == null ? "" : l.getCreatedAt().toString()
                )).toList();
        r.put("recent", recent);
        return r;
    }

    /** 데이터 규모 카운트 */
    private Map<String, Object> dataCounts() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("systems", systemRepo.count());
        r.put("companies", safeCount(() -> userRepo.findAll().stream().map(u -> u.getCompanyCd()).distinct().count()));
        r.put("departments", safeCount(() -> userRepo.findAll().stream().map(u -> u.getDeptId()).distinct().count()));
        r.put("users", userRepo.count());
        r.put("apis", apiRepo.count());
        r.put("menus", menuRepo.count());
        r.put("permissions", permissionRepo.count());
        return r;
    }

    /** 시스템별 짧은 통계 */
    private Map<String, Object> perSystemStats() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var s : systemRepo.findAll()) {
            String cd = s.getSystemCd();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("system_cd", cd);
            r.put("apis", apiRepo.findBySystemCd(cd).size());
            r.put("menus", menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc(cd).size());
            r.put("permissions", permissionRepo.findBySystemCd(cd).size());
            r.put("api_mapped", menuActionApiRepo.findMappedApiIdsForSystem(cd).size());
            Set<String> keys = redis.keys("perm:*:" + cd + ":*");
            r.put("redis_keys", keys == null ? 0 : keys.size());
            rows.add(r);
        }
        return Map.of("rows", rows);
    }

    private long safeCount(java.util.function.Supplier<Long> sup) {
        try { return sup.get(); } catch (Exception e) { return -1; }
    }

    private long parseLong(String v) {
        if (v == null) return 0;
        try { return Long.parseLong(v.trim()); } catch (Exception e) { return 0; }
    }
}
