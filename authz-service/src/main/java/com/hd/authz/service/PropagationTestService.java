package com.hd.authz.service;

import com.hd.authz.domain.*;
import com.hd.authz.repo.*;
import com.hd.authz.sync.SyncWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Measures end-to-end cache propagation time for write scenarios:
 *   1) PERM_GRANT / PERM_REVOKE — user permission
 *   2) UG_MEMBER_ADD — adding user to a permitted user group
 *   3) MENU_CREATE_LEAF — full lifecycle: create leaf menu + action + api map + permission
 *   4) MENU_ACTION_API_CHANGE — map a new API to an existing (menu, action)
 *
 * Each scenario measures:
 *   - write_us: DB+Outbox emit time
 *   - propagation_ms: time from write to first successful authz check that reflects the change
 *   - total_ms: write_us/1000 + propagation_ms
 *
 * If fastSync=true, calls SyncWorker.poll() right after the write — bypasses the natural 1s polling
 * interval and measures pure rebuild+invalidation cost.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropagationTestService {

    private final PermissionService permissionService;
    private final PermissionRepo permissionRepo;
    private final UserRepo userRepo;
    private final UserGroupRepo userGroupRepo;
    private final UserGroupMapRepo userGroupMapRepo;
    private final MenuRepo menuRepo;
    private final MenuImplRepo menuImplRepo;
    private final MenuActionRepo menuActionRepo;
    private final MenuActionApiRepo menuActionApiRepo;
    private final ApiRepo apiRepo;
    private final AuthzQueryService authz;
    private final WarmupService warmupService;
    private final SyncWorker syncWorker;

    private static final long MAX_WAIT_MS = 30_000;

    public Map<String, Object> run(PropRunReq req) {
        List<Map<String, Object>> scenarios = new ArrayList<>();
        if (req.scenarios == null || req.scenarios.isEmpty()) {
            req.scenarios = List.of("PERM_GRANT", "PERM_REVOKE", "UG_MEMBER_ADD",
                                    "MENU_CREATE_LEAF", "MENU_ACTION_API_CHANGE");
        }
        for (String s : req.scenarios) {
            try {
                Map<String, Object> r = switch (s) {
                    case "PERM_GRANT" -> runPermGrant(req.iterations, req.fastSync);
                    case "PERM_REVOKE" -> runPermRevoke(req.iterations, req.fastSync);
                    case "UG_MEMBER_ADD" -> runUgMemberAdd(req.iterations, req.fastSync);
                    case "MENU_CREATE_LEAF" -> runMenuCreateLeaf(req.iterations, req.fastSync);
                    case "MENU_ACTION_API_CHANGE" -> runMenuActionApiChange(req.iterations, req.fastSync);
                    default -> Map.of("scenario", s, "error", "unknown scenario");
                };
                scenarios.add(r);
            } catch (Exception e) {
                log.error("Scenario {} failed", s, e);
                scenarios.add(Map.of("scenario", s, "error", e.getMessage()));
            }
        }
        return Map.of("scenarios", scenarios, "fast_sync", req.fastSync);
    }

    // ============================================================
    // Scenario 1: PERM_GRANT
    // ============================================================
    private Map<String, Object> runPermGrant(int n, boolean fastSync) {
        UserEntity u = userRepo.findById("U00100").orElseGet(() ->
                userRepo.findAll().get(0));
        Menu menu = pickLeafWithApi();
        if (menu == null) return error("PERM_GRANT", "no leaf menu with API mapping found");
        String action = "R";
        ApiEntity api = pickApiForMenuAction(menu.getMenuId(), action);
        if (api == null) return error("PERM_GRANT", "no api mapped to menu/action");

        warmupService.warmupSystem("ERP", u.getUserId());
        // ensure clean state
        permissionRepo.findUnique("ERP", u.getCompanyCd(), "U", u.getUserId(), "M", menu.getMenuId(), action)
                .ifPresent(p -> permissionService.revoke(p.getPermId(), "proptest"));
        triggerSyncMaybe(fastSync);
        waitFor(() -> !authz.check("ERP", u.getCompanyCd(), u.getDeptId(), u.getUserId(),
                api.getHttpMethod(), api.getUrlPattern()), MAX_WAIT_MS);

        List<long[]> meas = new ArrayList<>();
        int ok = 0, timeout = 0;
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            Permission p = Permission.builder()
                    .systemCd("ERP").companyCd(u.getCompanyCd())
                    .subjectType("U").subjectId(u.getUserId())
                    .targetType("M").targetId(menu.getMenuId())
                    .actionCd(action).createdBy("proptest").updatedBy("proptest")
                    .build();
            Permission saved = permissionService.grant(p, "proptest");
            long t1 = System.nanoTime();

            triggerSyncMaybe(fastSync);
            long propMs = waitFor(() -> authz.check("ERP", u.getCompanyCd(), u.getDeptId(), u.getUserId(),
                    api.getHttpMethod(), api.getUrlPattern()), MAX_WAIT_MS);
            if (propMs >= 0) { ok++; meas.add(new long[]{(t1 - t0) / 1000, propMs}); }
            else timeout++;

            // cleanup
            permissionService.revoke(saved.getPermId(), "proptest");
            triggerSyncMaybe(fastSync);
            waitFor(() -> !authz.check("ERP", u.getCompanyCd(), u.getDeptId(), u.getUserId(),
                    api.getHttpMethod(), api.getUrlPattern()), MAX_WAIT_MS);
        }
        return result("PERM_GRANT", n, ok, timeout, meas);
    }

    // ============================================================
    // Scenario 2: PERM_REVOKE
    // ============================================================
    private Map<String, Object> runPermRevoke(int n, boolean fastSync) {
        UserEntity u = userRepo.findById("U00100").orElseGet(() -> userRepo.findAll().get(0));
        Menu menu = pickLeafWithApi();
        if (menu == null) return error("PERM_REVOKE", "no leaf");
        String action = "R";
        ApiEntity api = pickApiForMenuAction(menu.getMenuId(), action);
        if (api == null) return error("PERM_REVOKE", "no api");

        warmupService.warmupSystem("ERP", u.getUserId());

        List<long[]> meas = new ArrayList<>();
        int ok = 0, timeout = 0;
        for (int i = 0; i < n; i++) {
            // ensure perm exists
            Permission existing = permissionRepo.findUnique("ERP", u.getCompanyCd(), "U", u.getUserId(),
                    "M", menu.getMenuId(), action).orElse(null);
            if (existing == null) {
                Permission p = Permission.builder()
                        .systemCd("ERP").companyCd(u.getCompanyCd())
                        .subjectType("U").subjectId(u.getUserId())
                        .targetType("M").targetId(menu.getMenuId()).actionCd(action)
                        .createdBy("proptest").updatedBy("proptest").build();
                existing = permissionService.grant(p, "proptest");
                triggerSyncMaybe(fastSync);
                waitFor(() -> authz.check("ERP", u.getCompanyCd(), u.getDeptId(), u.getUserId(),
                        api.getHttpMethod(), api.getUrlPattern()), MAX_WAIT_MS);
            }

            long t0 = System.nanoTime();
            permissionService.revoke(existing.getPermId(), "proptest");
            long t1 = System.nanoTime();

            triggerSyncMaybe(fastSync);
            long propMs = waitFor(() -> !authz.check("ERP", u.getCompanyCd(), u.getDeptId(), u.getUserId(),
                    api.getHttpMethod(), api.getUrlPattern()), MAX_WAIT_MS);
            if (propMs >= 0) { ok++; meas.add(new long[]{(t1 - t0) / 1000, propMs}); }
            else timeout++;
        }
        return result("PERM_REVOKE", n, ok, timeout, meas);
    }

    // ============================================================
    // Scenario 3: UG_MEMBER_ADD
    // ============================================================
    private Map<String, Object> runUgMemberAdd(int n, boolean fastSync) {
        UserEntity u = userRepo.findById("U00150").orElseGet(() -> userRepo.findAll().get(0));
        Menu menu = pickLeafWithApi();
        if (menu == null) return error("UG_MEMBER_ADD", "no leaf");
        String action = "R";
        ApiEntity api = pickApiForMenuAction(menu.getMenuId(), action);
        if (api == null) return error("UG_MEMBER_ADD", "no api");

        // setup: create temp UG with permission on the menu/action
        UserGroup ug = userGroupRepo.save(UserGroup.builder()
                .companyCd(u.getCompanyCd()).groupNm("PROPTEST_UG").groupType("UG").build());
        Permission ugPerm = permissionService.grant(Permission.builder()
                .systemCd("ERP").companyCd(u.getCompanyCd())
                .subjectType("UG").subjectId(String.valueOf(ug.getUserGroupId()))
                .targetType("M").targetId(menu.getMenuId()).actionCd(action)
                .createdBy("proptest").updatedBy("proptest").build(), "proptest");
        triggerSyncMaybe(fastSync);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        warmupService.warmupSystem("ERP", u.getUserId());
        // remove user from group if any
        userGroupMapRepo.findAll().stream()
                .filter(m -> m.getUserGroupId().equals(ug.getUserGroupId()) && m.getUserId().equals(u.getUserId()))
                .forEach(userGroupMapRepo::delete);

        List<long[]> meas = new ArrayList<>();
        int ok = 0, timeout = 0;
        try {
            for (int i = 0; i < n; i++) {
                long t0 = System.nanoTime();
                userGroupMapRepo.save(UserGroupMap.builder()
                        .userGroupId(ug.getUserGroupId()).userId(u.getUserId()).build());
                // emit a UG_MEMBER_ADD event (UG affects a single user → rebuild user U)
                permissionService.emit("UG_MEMBER_ADD", "ERP", "U", u.getUserId(),
                        Map.of("user_group_id", ug.getUserGroupId(), "user_id", u.getUserId()));
                long t1 = System.nanoTime();
                triggerSyncMaybe(fastSync);
                long propMs = waitFor(() -> authz.check("ERP", u.getCompanyCd(), u.getDeptId(), u.getUserId(),
                        api.getHttpMethod(), api.getUrlPattern()), MAX_WAIT_MS);
                if (propMs >= 0) { ok++; meas.add(new long[]{(t1 - t0) / 1000, propMs}); }
                else timeout++;

                // cleanup: remove member
                UserGroupMapId mid = new UserGroupMapId();
                mid.setUserGroupId(ug.getUserGroupId()); mid.setUserId(u.getUserId());
                userGroupMapRepo.deleteById(mid);
                permissionService.emit("UG_MEMBER_DEL", "ERP", "U", u.getUserId(),
                        Map.of("user_group_id", ug.getUserGroupId(), "user_id", u.getUserId()));
                triggerSyncMaybe(fastSync);
                waitFor(() -> !authz.check("ERP", u.getCompanyCd(), u.getDeptId(), u.getUserId(),
                        api.getHttpMethod(), api.getUrlPattern()), MAX_WAIT_MS);
            }
        } finally {
            // teardown
            permissionService.revoke(ugPerm.getPermId(), "proptest");
            userGroupMapRepo.findAll().stream()
                    .filter(m -> m.getUserGroupId().equals(ug.getUserGroupId()))
                    .forEach(userGroupMapRepo::delete);
            userGroupRepo.deleteById(ug.getUserGroupId());
        }
        return result("UG_MEMBER_ADD", n, ok, timeout, meas);
    }

    // ============================================================
    // Scenario 4: MENU_CREATE_LEAF
    // ============================================================
    private Map<String, Object> runMenuCreateLeaf(int n, boolean fastSync) {
        UserEntity u = userRepo.findById("U00200").orElseGet(() -> userRepo.findAll().get(0));
        // pick a folder under ERP to attach test leaves to (use first folder)
        Menu folder = menuRepo.findBySystemCdOrderBySortOrderAscMenuIdAsc("ERP").stream()
                .filter(m -> "F".equals(m.getMenuType())).findFirst().orElse(null);
        if (folder == null) return error("MENU_CREATE_LEAF", "no folder in ERP");
        // pick an unused API
        ApiEntity api = apiRepo.findBySystemCd("ERP").stream()
                .filter(a -> menuActionApiRepo.findAll().stream().noneMatch(m -> m.getApiId().equals(a.getApiId())))
                .findFirst()
                .orElseGet(() -> apiRepo.findBySystemCd("ERP").get(0));

        warmupService.warmupSystem("ERP", u.getUserId());

        List<long[]> meas = new ArrayList<>();
        int ok = 0, timeout = 0;
        for (int i = 0; i < n; i++) {
            // 1) create leaf
            long t0 = System.nanoTime();
            Menu leaf = menuRepo.save(Menu.builder()
                    .systemCd("ERP").parentMenuId(folder.getMenuId()).menuType("M")
                    .menuCd("PROPTEST_LEAF_" + i + "_" + System.currentTimeMillis())
                    .menuNm("PropTest Leaf " + i).status("A").isVisible("Y")
                    .createdBy("proptest").updatedBy("proptest").build());
            menuImplRepo.save(MenuImpl.builder().menuId(leaf.getMenuId())
                    .routePath("/proptest/" + i).build());
            menuActionRepo.save(MenuAction.builder().menuId(leaf.getMenuId()).actionCd("R").build());
            menuActionApiRepo.save(MenuActionApi.builder()
                    .menuId(leaf.getMenuId()).actionCd("R").apiId(api.getApiId()).build());
            // grant permission to test user
            Permission perm = permissionService.grant(Permission.builder()
                    .systemCd("ERP").companyCd(u.getCompanyCd())
                    .subjectType("U").subjectId(u.getUserId())
                    .targetType("M").targetId(leaf.getMenuId()).actionCd("R")
                    .createdBy("proptest").updatedBy("proptest").build(), "proptest");
            permissionService.emit("MENU_TREE_CHANGE", "ERP", "U", u.getUserId(),
                    Map.of("menu_id", leaf.getMenuId(), "op", "ADD"));
            long t1 = System.nanoTime();

            triggerSyncMaybe(fastSync);
            long propMs = waitFor(() -> authz.check("ERP", u.getCompanyCd(), u.getDeptId(), u.getUserId(),
                    api.getHttpMethod(), api.getUrlPattern()), MAX_WAIT_MS);
            if (propMs >= 0) { ok++; meas.add(new long[]{(t1 - t0) / 1000, propMs}); }
            else timeout++;

            // cleanup
            permissionService.revoke(perm.getPermId(), "proptest");
            menuActionApiRepo.findByMenuId(leaf.getMenuId()).forEach(menuActionApiRepo::delete);
            menuActionRepo.findByMenuId(leaf.getMenuId()).forEach(menuActionRepo::delete);
            menuImplRepo.findById(leaf.getMenuId()).ifPresent(menuImplRepo::delete);
            menuRepo.delete(leaf);
            triggerSyncMaybe(fastSync);
            waitFor(() -> !authz.check("ERP", u.getCompanyCd(), u.getDeptId(), u.getUserId(),
                    api.getHttpMethod(), api.getUrlPattern()), MAX_WAIT_MS);
        }
        return result("MENU_CREATE_LEAF", n, ok, timeout, meas);
    }

    // ============================================================
    // Scenario 5: MENU_ACTION_API_CHANGE
    // ============================================================
    private Map<String, Object> runMenuActionApiChange(int n, boolean fastSync) {
        UserEntity u = userRepo.findById("U00010").orElseGet(() -> userRepo.findAll().get(0));
        Menu menu = pickLeafWithApi();
        if (menu == null) return error("MENU_ACTION_API_CHANGE", "no leaf");
        String action = "R";
        // ensure user has perm on (menu, action)
        permissionRepo.findUnique("ERP", u.getCompanyCd(), "U", u.getUserId(), "M", menu.getMenuId(), action)
                .orElseGet(() -> permissionService.grant(Permission.builder()
                        .systemCd("ERP").companyCd(u.getCompanyCd())
                        .subjectType("U").subjectId(u.getUserId())
                        .targetType("M").targetId(menu.getMenuId()).actionCd(action)
                        .createdBy("proptest").updatedBy("proptest").build(), "proptest"));

        // pick an unmapped API
        Set<Long> mapped = new HashSet<>();
        menuActionApiRepo.findAll().forEach(m -> mapped.add(m.getApiId()));
        ApiEntity targetApi = apiRepo.findBySystemCd("ERP").stream()
                .filter(a -> !mapped.contains(a.getApiId()))
                .findFirst()
                .orElseGet(() -> apiRepo.findBySystemCd("ERP").get(apiRepo.findBySystemCd("ERP").size() - 1));

        triggerSyncMaybe(fastSync);
        warmupService.warmupSystem("ERP", u.getUserId());

        List<long[]> meas = new ArrayList<>();
        int ok = 0, timeout = 0;
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            menuActionApiRepo.save(MenuActionApi.builder()
                    .menuId(menu.getMenuId()).actionCd(action).apiId(targetApi.getApiId()).build());
            permissionService.emit("MENU_ACTION_API_CHANGE", "ERP", "U", u.getUserId(),
                    Map.of("menu_id", menu.getMenuId(), "action_cd", action, "api_id", targetApi.getApiId()));
            long t1 = System.nanoTime();

            triggerSyncMaybe(fastSync);
            long propMs = waitFor(() -> authz.check("ERP", u.getCompanyCd(), u.getDeptId(), u.getUserId(),
                    targetApi.getHttpMethod(), targetApi.getUrlPattern()), MAX_WAIT_MS);
            if (propMs >= 0) { ok++; meas.add(new long[]{(t1 - t0) / 1000, propMs}); }
            else timeout++;

            // cleanup
            MenuActionApiId mid = new MenuActionApiId();
            mid.setMenuId(menu.getMenuId()); mid.setActionCd(action); mid.setApiId(targetApi.getApiId());
            menuActionApiRepo.deleteById(mid);
            permissionService.emit("MENU_ACTION_API_CHANGE", "ERP", "U", u.getUserId(),
                    Map.of("menu_id", menu.getMenuId(), "action_cd", action, "api_id", targetApi.getApiId()));
            triggerSyncMaybe(fastSync);
            waitFor(() -> !authz.check("ERP", u.getCompanyCd(), u.getDeptId(), u.getUserId(),
                    targetApi.getHttpMethod(), targetApi.getUrlPattern()), MAX_WAIT_MS);
        }
        return result("MENU_ACTION_API_CHANGE", n, ok, timeout, meas);
    }

    // ============================================================
    // Helpers
    // ============================================================
    private void triggerSyncMaybe(boolean fastSync) {
        if (!fastSync) return;
        try { syncWorker.poll(); } catch (Exception e) { log.warn("Sync poll failed: {}", e.toString()); }
    }

    /** Returns elapsed ms when predicate becomes true, or -1 on timeout. Polls every 5ms. */
    private long waitFor(BooleanSupplier predicate, long maxMs) {
        long start = System.nanoTime();
        long end = start + maxMs * 1_000_000L;
        while (System.nanoTime() < end) {
            try {
                if (predicate.getAsBoolean()) return (System.nanoTime() - start) / 1_000_000L;
            } catch (Exception ignored) {}
            try { Thread.sleep(5); } catch (InterruptedException e) { return -1; }
        }
        return -1;
    }

    private Menu pickLeafWithApi() {
        for (Menu m : menuRepo.findLeafMenusBySystem("ERP")) {
            if (!menuActionApiRepo.findByMenuId(m.getMenuId()).isEmpty()) return m;
        }
        return null;
    }

    private ApiEntity pickApiForMenuAction(Long menuId, String actionCd) {
        var maps = menuActionApiRepo.findByMenuIdAndActionCd(menuId, actionCd);
        if (maps.isEmpty()) return null;
        return apiRepo.findById(maps.get(0).getApiId()).orElse(null);
    }

    private Map<String, Object> result(String scenario, int total, int ok, int timeouts, List<long[]> meas) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scenario", scenario);
        out.put("iterations", total);
        out.put("success", ok);
        out.put("timeouts", timeouts);
        if (meas.isEmpty()) {
            out.put("write_us", Map.of("count", 0));
            out.put("propagation_ms", Map.of("count", 0));
            out.put("total_ms", Map.of("count", 0));
            return out;
        }
        long[] writes = meas.stream().mapToLong(m -> m[0]).sorted().toArray();
        long[] props = meas.stream().mapToLong(m -> m[1]).sorted().toArray();
        long[] totals = meas.stream().mapToLong(m -> m[0] / 1000 + m[1]).sorted().toArray();
        out.put("write_us", percentiles(writes));
        out.put("propagation_ms", percentiles(props));
        out.put("total_ms", percentiles(totals));
        // also keep individual samples for chart
        out.put("samples", meas.stream().map(m -> Map.of("write_us", m[0], "prop_ms", m[1])).toList());
        return out;
    }

    private Map<String, Object> percentiles(long[] sorted) {
        if (sorted.length == 0) return Map.of("count", 0);
        double avg = Arrays.stream(sorted).average().orElse(0);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("count", sorted.length);
        p.put("min", sorted[0]);
        p.put("max", sorted[sorted.length - 1]);
        p.put("avg", (long) avg);
        p.put("p50", sorted[(int) (sorted.length * 0.50)]);
        p.put("p95", sorted[(int) (sorted.length * 0.95)]);
        p.put("p99", sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.99))]);
        return p;
    }

    private Map<String, Object> error(String scenario, String message) {
        return Map.of("scenario", scenario, "error", message);
    }

    public static class PropRunReq {
        public List<String> scenarios;     // null/empty → all
        public int iterations = 5;
        public boolean fastSync = false;   // true → SyncWorker.poll() right after each write
    }
}
