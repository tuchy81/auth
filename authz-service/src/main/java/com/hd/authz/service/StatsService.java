package com.hd.authz.service;

import com.hd.authz.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final MenuRepo menuRepo;
    private final ApiRepo apiRepo;
    private final PermissionRepo permissionRepo;
    private final UserRepo userRepo;
    private final ShardConfigRepo shardConfigRepo;
    private final MenuActionApiRepo menuActionApiRepo;
    private final StringRedisTemplate redis;

    public Map<String, Object> systemStats(String systemCd) {
        long menus = menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc(systemCd).size();
        long apis = apiRepo.findBySystemCd(systemCd).size();
        long perms = permissionRepo.findAll().stream().filter(p -> p.getSystemCd().equals(systemCd)).count();
        long activeUsers = userRepo.findAll().stream().filter(u -> "A".equals(u.getStatus())).count();
        String shardStrategy = shardConfigRepo.findById(systemCd).map(c -> c.getShardStrategy()).orElse("METHOD_DEPTH");

        // mapping counts (filter by system_cd)
        Set<Long> apiIdsInSystem = apiRepo.findBySystemCd(systemCd).stream()
                .map(a -> a.getApiId()).collect(java.util.stream.Collectors.toSet());
        long mappedApiIds = menuActionApiRepo.findAll().stream()
                .map(m -> m.getApiId())
                .filter(apiIdsInSystem::contains)
                .distinct().count();
        long unmapped = apiIdsInSystem.size() - mappedApiIds;

        // redis cache size (total keys for this system)
        Set<String> keys = redis.keys("perm:*:" + systemCd + ":*");
        long cacheKeys = keys == null ? 0 : keys.size();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("menus", menus);
        out.put("apis", apis);
        out.put("api_mapped", mappedApiIds);
        out.put("api_unmapped", unmapped);
        out.put("permissions", perms);
        out.put("active_users", activeUsers);
        out.put("shard_strategy", shardStrategy);
        out.put("cache_keys", cacheKeys);
        return out;
    }
}
