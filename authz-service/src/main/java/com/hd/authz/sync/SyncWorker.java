package com.hd.authz.sync;

import com.hd.authz.cache.PermCacheService;
import com.hd.authz.config.RedisConfig;
import com.hd.authz.domain.Menu;
import com.hd.authz.domain.PermChangeLog;
import com.hd.authz.domain.Permission;
import com.hd.authz.domain.UserEntity;
import com.hd.authz.repo.MenuRepo;
import com.hd.authz.repo.PermChangeLogRepo;
import com.hd.authz.repo.PermissionRepo;
import com.hd.authz.repo.UserGroupMapRepo;
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
import java.util.*;

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
    private final MenuRepo menuRepo;
    private final PermissionRepo permissionRepo;
    private final UserGroupMapRepo userGroupMapRepo;

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
        if (!invalidatedKeys.isEmpty()) {
            redis.convertAndSend(RedisConfig.INVALIDATION_CHANNEL, String.join("|", invalidatedKeys));
            invalidatedKeys.forEach(cache::invalidateLocal);
        }
        log.info("Sync processed {} events, {} keys invalidated", batch.size(), invalidatedKeys.size());
    }

    private Set<String> handle(PermChangeLog ev) {
        String type = ev.getEventType();
        String system = ev.getSystemCd();
        Set<String> keys = new HashSet<>();
        switch (type) {
            case "PERM_GRANT", "PERM_REVOKE" -> {
                // Direct subject rebuild — scope_type/scope_id 가 권한 소유자
                rebuildScope(system, ev.getScopeType(), ev.getScopeId(), keys);
                if (system != null) apiMetaService.invalidate(system);
            }

            // 스펙 §8.6 — 메뉴 트리/액션 매핑 변경 시:
            //   1) 변경된 menu_id 의 자기 자신 + 조상 폴더 식별
            //   2) TB_PERMISSION 에서 그 menu_id 들에 권한이 있는 모든 subject 추출
            //   3) 각 subject 캐시 rebuild (UG/DG/CG 는 멤버 사용자까지 fan-out)
            case "MENU_TREE_CHANGE", "MENU_ACTION_API_CHANGE", "MENU_ACTION_CHANGE",
                 "MENU_TYPE_CHANGE" -> {
                Long menuId = readMenuId(ev);
                if (menuId == null) {
                    log.warn("{} event without menu_id payload — skipped (seq={})", type, ev.getSeq());
                    break;
                }
                Set<Long> affectedMenus = findMenuAndAncestors(menuId);
                Set<String> doneSubjects = new HashSet<>();
                for (Long mid : affectedMenus) {
                    for (Permission p : permissionRepo.findBySystemCdAndTargetTypeAndTargetId(system, "M", mid)) {
                        String dedupe = p.getSubjectType() + "|" + p.getSubjectId();
                        if (!doneSubjects.add(dedupe)) continue;
                        rebuildScope(system, p.getSubjectType(), p.getSubjectId(), keys);
                    }
                }
                if (system != null) apiMetaService.invalidate(system);
                log.info("{} on menu {} → ancestors {}, rebuilt {} subjects", type, menuId, affectedMenus, doneSubjects.size());
            }

            case "UG_MEMBER_ADD", "UG_MEMBER_DEL" -> {
                // payload.user_id 의 U-level 캐시만 rebuild 하면 충분 (U-level 이 UG 권한 포함)
                String userId = (String) (ev.getPayload() == null ? null : ev.getPayload().get("user_id"));
                if (userId != null) {
                    keys.addAll(flattener.rebuildSubject(system, "U", userId));
                }
            }

            case "DG_MEMBER_ADD", "DG_MEMBER_DEL", "CG_MEMBER_ADD", "CG_MEMBER_DEL" -> {
                // 그룹 멤버 변동: 영향 사용자 전체에 대해 rebuild — 단순 fan-out
                rebuildScope(system, ev.getScopeType(), ev.getScopeId(), keys);
            }

            case "USER_DEPT_CHANGE" -> {
                String userId = ev.getScopeId();
                if (userId != null) keys.addAll(flattener.rebuildSubject(system, "U", userId));
            }

            case "SHARD_STRATEGY_CHANGE", "SEGMENT_POSITION_CHANGE" -> {
                // 시스템 전체 캐시 무효화 — 모든 사용자 + 영향받는 회사/부서 rebuild
                rebuildSystemAll(system, keys);
                apiMetaService.invalidate(system);
            }

            case "API_META_CHANGE" -> apiMetaService.invalidate(system);

            default -> log.warn("Unknown event type {}", type);
        }
        return keys;
    }

    private void rebuildScope(String system, String scopeType, String scopeId, Set<String> keys) {
        if (scopeType == null || scopeId == null) return;
        switch (scopeType) {
            case "C" -> {
                keys.addAll(flattener.rebuildSubject(system, "C", scopeId));
                // 회사 산하 모든 사용자의 U-level 도 갱신 (U-level 가 회사 perm 을 흡수)
                userRepo.findAll().stream()
                        .filter(u -> scopeId.equals(u.getCompanyCd()))
                        .forEach(u -> keys.addAll(flattener.rebuildSubject(system, "U", u.getUserId())));
            }
            case "D" -> {
                keys.addAll(flattener.rebuildSubject(system, "D", scopeId));
                userRepo.findAll().stream()
                        .filter(u -> scopeId.equals(u.getDeptId()))
                        .forEach(u -> keys.addAll(flattener.rebuildSubject(system, "U", u.getUserId())));
            }
            case "U" -> keys.addAll(flattener.rebuildSubject(system, "U", scopeId));
            case "UG" -> {
                long gid;
                try { gid = Long.parseLong(scopeId); } catch (NumberFormatException e) { return; }
                userGroupMapRepo.findAll().stream()
                        .filter(m -> m.getUserGroupId().equals(gid))
                        .forEach(m -> keys.addAll(flattener.rebuildSubject(system, "U", m.getUserId())));
            }
            // CG/DG: 추후 멤버 expand 보완
            default -> log.warn("rebuildScope: subjectType '{}' not yet supported", scopeType);
        }
    }

    private void rebuildSystemAll(String system, Set<String> keys) {
        for (UserEntity u : userRepo.findAll()) {
            keys.addAll(flattener.rebuildSubject(system, "U", u.getUserId()));
        }
    }

    /** Walks parent_menu_id chain. Returns {menuId} ∪ all ancestors. */
    private Set<Long> findMenuAndAncestors(Long menuId) {
        Set<Long> set = new LinkedHashSet<>();
        Long cur = menuId;
        int safety = 100;
        while (cur != null && safety-- > 0) {
            if (!set.add(cur)) break;
            Optional<Menu> m = menuRepo.findById(cur);
            if (m.isEmpty()) break;
            cur = m.get().getParentMenuId();
        }
        return set;
    }

    private Long readMenuId(PermChangeLog ev) {
        if (ev.getPayload() == null) return null;
        Object v = ev.getPayload().get("menu_id");
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    /** Trigger full rebuild for all users in a system (used at boot). */
    public void rebuildAllUsers(String systemCd) {
        for (UserEntity u : userRepo.findAll()) {
            flattener.rebuildSubject(systemCd, "U", u.getUserId());
        }
    }
}
