package com.hd.authz.sync;

import com.hd.authz.cache.PermCacheService;
import com.hd.authz.config.RedisConfig;
import com.hd.authz.domain.PermChangeLog;
import com.hd.authz.domain.UserEntity;
import com.hd.authz.repo.PermChangeLogRepo;
import com.hd.authz.repo.UserRepo;
import com.hd.authz.service.ApiMetaService;
import com.hd.authz.service.PermissionFlattener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncWorker {

    private final PermChangeLogRepo logRepo;
    private final PermissionFlattener flattener;
    private final UserRepo userRepo;
    private final ApiMetaService apiMetaService;
    private final PermCacheService cache;
    private final StringRedisTemplate redis;

    @Value("${authz.sync.batch-size:200}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${authz.sync.poll-interval-ms:1000}")
    @Transactional
    public void poll() {
        List<PermChangeLog> batch = logRepo.findUnprocessed(PageRequest.of(0, batchSize));
        if (batch.isEmpty()) return;
        Set<String> invalidatedKeys = new HashSet<>();
        for (PermChangeLog ev : batch) {
            try {
                invalidatedKeys.addAll(handle(ev));
                ev.setProcessedYn("Y");
                ev.setProcessedAt(LocalDateTime.now());
            } catch (Exception e) {
                log.error("Sync failed seq={} type={}: {}", ev.getSeq(), ev.getEventType(), e.toString(), e);
            }
        }
        logRepo.saveAll(batch);
        // pub/sub invalidation to other nodes
        if (!invalidatedKeys.isEmpty()) {
            redis.convertAndSend(RedisConfig.INVALIDATION_CHANNEL, String.join("|", invalidatedKeys));
            invalidatedKeys.forEach(cache::invalidateLocal);
        }
        log.info("Sync processed {} events, {} keys invalidated", batch.size(), invalidatedKeys.size());
    }

    private Set<String> handle(PermChangeLog ev) {
        String type = ev.getEventType();
        Set<String> keys = new HashSet<>();
        switch (type) {
            case "PERM_GRANT", "PERM_REVOKE", "MENU_TREE_CHANGE", "MENU_ACTION_API_CHANGE" -> {
                String system = ev.getSystemCd();
                String scopeType = ev.getScopeType();
                String scopeId = ev.getScopeId();
                if ("C".equals(scopeType)) {
                    keys.addAll(flattener.rebuildSubject(system, "C", scopeId));
                } else if ("D".equals(scopeType)) {
                    keys.addAll(flattener.rebuildSubject(system, "D", scopeId));
                } else if ("U".equals(scopeType)) {
                    keys.addAll(flattener.rebuildSubject(system, "U", scopeId));
                } else if ("UG".equals(scopeType)) {
                    // rebuild members - for MVP: rebuild all users (or fan out)
                    log.info("UG scope rebuild not fully implemented yet");
                }
                // also bust API meta on api/menu changes
                if (system != null) apiMetaService.invalidate(system);
            }
            case "API_META_CHANGE" -> apiMetaService.invalidate(ev.getSystemCd());
            default -> log.warn("Unknown event type {}", type);
        }
        return keys;
    }

    /** Trigger full rebuild for all users in a system (used at boot or on bulk import). */
    public void rebuildAllUsers(String systemCd) {
        for (UserEntity u : userRepo.findAll()) {
            flattener.rebuildSubject(systemCd, "U", u.getUserId());
        }
    }
}
