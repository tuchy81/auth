package com.hd.authz.api;

import com.hd.authz.cache.PermCacheService;
import com.hd.authz.service.AuthzQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/authz")
@RequiredArgsConstructor
public class AuthzController {

    private final AuthzQueryService query;
    private final PermCacheService cache;

    /** spec §7.1.1 */
    @GetMapping("/check")
    public Map<String, Object> check(@RequestParam("system_cd") String systemCd,
                                     @RequestParam("company_cd") String companyCd,
                                     @RequestParam("dept_id") String deptId,
                                     @RequestParam("user_id") String userId,
                                     @RequestParam("method") String method,
                                     @RequestParam("api_url") String apiUrl) {
        boolean allowed = query.check(systemCd, companyCd, deptId, userId, method, apiUrl);
        return Map.of("allowed", allowed);
    }

    /** spec §7.1.4 */
    @PostMapping("/check-batch")
    public Map<String, Object> checkBatch(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) body.get("items");
        List<Boolean> rs = query.checkBatch(
                (String) body.get("system_cd"),
                (String) body.get("company_cd"),
                (String) body.get("dept_id"),
                (String) body.get("user_id"),
                items
        );
        return Map.of("results", rs.stream().map(b -> Map.of("allowed", b)).toList());
    }

    /** spec §7.1.2 */
    @GetMapping("/menu-actions")
    public Map<String, Object> menuActions(@RequestParam("system_cd") String systemCd,
                                           @RequestParam("company_cd") String companyCd,
                                           @RequestParam("dept_id") String deptId,
                                           @RequestParam("user_id") String userId,
                                           @RequestParam("menu_id") Long menuId) {
        Set<String> actions = query.menuActions(systemCd, companyCd, deptId, userId, menuId);
        return Map.of("allowed_actions", actions);
    }

    /** spec §7.1.3 */
    @PostMapping("/menu-tree")
    public Map<String, Object> menuTree(@RequestBody Map<String, String> body) {
        return Map.of("tree", query.menuTree(
                body.get("system_cd"), body.get("company_cd"), body.get("dept_id"), body.get("user_id")));
    }

    @GetMapping("/cache/stats")
    public Map<String, Object> cacheStats() {
        return cache.stats();
    }
}
