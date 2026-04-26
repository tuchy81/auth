package com.hd.authz.cache;

import com.hd.authz.common.UrlUtils;
import com.hd.authz.domain.SystemShardConfig;

import java.util.List;

/**
 * Builds Redis/Caffeine keys per spec §6.3.
 * Format: perm:api:{system_cd}:{level}:{level_id...}:{shard}
 */
public final class ShardKeyBuilder {
    private ShardKeyBuilder() {}

    public static String shard(String method, int depth, String seg, SystemShardConfig cfg) {
        if (cfg != null && "METHOD_DEPTH_SEG".equals(cfg.getShardStrategy())) {
            return method + ":" + depth + ":" + (seg == null ? cfg.getSegmentFallback() : seg);
        }
        return method + ":" + depth;
    }

    public static String extractSeg(String urlPattern, SystemShardConfig cfg) {
        if (cfg == null || !"METHOD_DEPTH_SEG".equals(cfg.getShardStrategy())) return null;
        List<String> segs = UrlUtils.segments(urlPattern);
        int pos = cfg.getSegmentPosition() == null ? 0 : cfg.getSegmentPosition();
        if (pos >= segs.size()) return cfg.getSegmentFallback();
        String seg = segs.get(pos);
        if (seg.startsWith("{") && seg.endsWith("}")) return "_var_" + pos;
        int max = cfg.getSegmentMaxLength() == null ? 32 : cfg.getSegmentMaxLength();
        return seg.length() > max ? seg.substring(0, max) : seg;
    }

    public static String companyKey(String system, String company, String shard) {
        return "perm:api:" + system + ":C:" + company + ":" + shard;
    }

    public static String deptKey(String system, String company, String dept, String shard) {
        return "perm:api:" + system + ":D:" + company + ":" + dept + ":" + shard;
    }

    public static String userKey(String system, String user, String shard) {
        return "perm:api:" + system + ":U:" + user + ":" + shard;
    }

    public static String menuActionCompanyKey(String system, String company, String action) {
        return "perm:menu_action:" + system + ":C:" + company + ":" + action;
    }

    public static String menuActionDeptKey(String system, String company, String dept, String action) {
        return "perm:menu_action:" + system + ":D:" + company + ":" + dept + ":" + action;
    }

    public static String menuActionUserKey(String system, String user, String action) {
        return "perm:menu_action:" + system + ":U:" + user + ":" + action;
    }
}
