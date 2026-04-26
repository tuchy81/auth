package com.hd.authz.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.hd.authz.common.UrlUtils;
import com.hd.authz.repo.ApiRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves "{method} {url}" → api_id for a system. URL patterns may contain {var} placeholders.
 */
@Service
@RequiredArgsConstructor
public class ApiMetaService {

    private final ApiRepo apiRepo;
    private final Cache<String, Object> metaCache;

    @SuppressWarnings("unchecked")
    public Map<String, ApiMeta> getRouteMap(String systemCd) {
        return (Map<String, ApiMeta>) metaCache.get("api:route:" + systemCd, k -> {
            Map<String, ApiMeta> map = new HashMap<>();
            apiRepo.findBySystemCd(systemCd).forEach(a -> {
                String key = a.getHttpMethod() + ":" + UrlUtils.normalize(a.getUrlPattern());
                map.put(key, new ApiMeta(a.getApiId(), a.getUrlPattern(), a.getUrlDepth(), a.getShardSeg()));
            });
            return map;
        });
    }

    public ApiMeta resolve(String systemCd, String method, String url) {
        Map<String, ApiMeta> map = getRouteMap(systemCd);
        String exact = method + ":" + UrlUtils.normalize(url);
        ApiMeta hit = map.get(exact);
        if (hit != null) return hit;

        // Pattern match: try entries with same method and same depth using regex on {var}
        int depth = UrlUtils.depth(url);
        for (Map.Entry<String, ApiMeta> e : map.entrySet()) {
            ApiMeta m = e.getValue();
            if (m.depth() != depth) continue;
            if (!e.getKey().startsWith(method + ":")) continue;
            if (matches(m.urlPattern(), url)) return m;
        }
        return null;
    }

    public void invalidate(String systemCd) {
        metaCache.invalidate("api:route:" + systemCd);
    }

    private boolean matches(String pattern, String url) {
        String regex = "^" + Pattern.quote(UrlUtils.normalize(pattern))
                .replaceAll("\\\\\\{[^}]+\\\\\\}", "\\\\E[^/]+\\\\Q") + "$";
        return UrlUtils.normalize(url).matches(regex);
    }

    public record ApiMeta(Long apiId, String urlPattern, int depth, String shardSeg) {}
}
