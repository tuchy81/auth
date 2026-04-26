package com.hd.authz.service;

import com.hd.authz.domain.ApiEntity;
import com.hd.authz.domain.UserEntity;
import com.hd.authz.repo.ApiRepo;
import com.hd.authz.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process load tester for the authz check path.
 * Calls AuthzQueryService.check() directly (bypassing HTTP) — measures the cache+lookup pipeline only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfTestService {

    private final AuthzQueryService authz;
    private final UserRepo userRepo;
    private final ApiRepo apiRepo;
    private final WarmupService warmup;

    /** Pre-built test data. */
    private record TestData(List<UserCtx> users, List<UrlCtx> urls) {}
    private record UserCtx(String userId, String companyCd, String deptId) {}
    private record UrlCtx(String method, String urlPattern) {}

    public Map<String, Object> run(PerfRunReq req) {
        TestData td = buildTestData(req);
        if (td.users.isEmpty() || td.urls.isEmpty()) {
            return Map.of("error", "no test data — seed the DB first");
        }

        if (req.warmup) {
            // warm a sample of users
            for (int i = 0; i < Math.min(20, td.users.size()); i++) {
                try { warmup.warmupSystem(req.systemCd, td.users.get(i).userId); } catch (Exception ignored) {}
            }
        }

        return executeOne(req, td, true);
    }

    public Map<String, Object> sweep(PerfSweepReq req) {
        TestData td = buildTestData(toRunReq(req, req.concurrencyLevels.get(0)));
        if (req.warmup) {
            for (int i = 0; i < Math.min(20, td.users.size()); i++) {
                try { warmup.warmupSystem(req.systemCd, td.users.get(i).userId); } catch (Exception ignored) {}
            }
        }
        List<Map<String, Object>> levels = new ArrayList<>();
        for (int c : req.concurrencyLevels) {
            PerfRunReq r = toRunReq(req, c);
            r.warmup = false;
            Map<String, Object> result = executeOne(r, td, false);
            result.put("concurrency", c);
            levels.add(result);
            // brief pause between levels
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
        return Map.of("levels", levels, "system_cd", req.systemCd);
    }

    private PerfRunReq toRunReq(PerfSweepReq req, int conc) {
        PerfRunReq r = new PerfRunReq();
        r.systemCd = req.systemCd;
        r.concurrency = conc;
        r.durationSec = req.durationSecPerLevel;
        r.warmup = false;
        return r;
    }

    private TestData buildTestData(PerfRunReq req) {
        // sample 100 users (or all if fewer)
        List<UserEntity> allUsers = userRepo.findAll();
        Collections.shuffle(allUsers);
        int n = Math.min(req.userSample > 0 ? req.userSample : 100, allUsers.size());
        List<UserCtx> users = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UserEntity u = allUsers.get(i);
            users.add(new UserCtx(u.getUserId(), u.getCompanyCd(), u.getDeptId()));
        }
        // all APIs for system (sample if huge)
        List<ApiEntity> apis = apiRepo.findBySystemCd(req.systemCd);
        Collections.shuffle(apis);
        int m = Math.min(req.urlSample > 0 ? req.urlSample : 500, apis.size());
        List<UrlCtx> urls = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            ApiEntity a = apis.get(i);
            urls.add(new UrlCtx(a.getHttpMethod(), a.getUrlPattern()));
        }
        return new TestData(users, urls);
    }

    private Map<String, Object> executeOne(PerfRunReq req, TestData td, boolean withTimeline) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, req.concurrency));
        ConcurrentLinkedQueue<Long> latNs = new ConcurrentLinkedQueue<>();
        AtomicLong allowed = new AtomicLong(), denied = new AtomicLong(), errors = new AtomicLong();
        // timeline buckets: per-second
        int durSec = Math.max(1, req.durationSec);
        AtomicLong[] perSecCount = new AtomicLong[durSec];
        AtomicLong[] perSecLatSum = new AtomicLong[durSec];
        for (int i = 0; i < durSec; i++) { perSecCount[i] = new AtomicLong(); perSecLatSum[i] = new AtomicLong(); }

        long start = System.nanoTime();
        long deadline = start + (long) durSec * 1_000_000_000L;
        CountDownLatch done = new CountDownLatch(req.concurrency);

        for (int t = 0; t < req.concurrency; t++) {
            pool.submit(() -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                try {
                    while (true) {
                        long now = System.nanoTime();
                        if (now >= deadline) break;
                        UserCtx u = td.users.get(rnd.nextInt(td.users.size()));
                        UrlCtx url = td.urls.get(rnd.nextInt(td.urls.size()));
                        long t0 = System.nanoTime();
                        try {
                            boolean allow = authz.check(req.systemCd, u.companyCd, u.deptId, u.userId, url.method, url.urlPattern);
                            long t1 = System.nanoTime();
                            long lat = t1 - t0;
                            latNs.add(lat);
                            if (allow) allowed.incrementAndGet(); else denied.incrementAndGet();
                            int sec = (int) Math.min(durSec - 1, (t1 - start) / 1_000_000_000L);
                            perSecCount[sec].incrementAndGet();
                            perSecLatSum[sec].addAndGet(lat);
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                } finally { done.countDown(); }
            });
        }

        try { done.await(durSec + 5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        long elapsedNs = System.nanoTime() - start;
        pool.shutdownNow();

        long[] arr = latNs.stream().mapToLong(Long::longValue).sorted().toArray();
        long total = arr.length + errors.get();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("system_cd", req.systemCd);
        out.put("concurrency", req.concurrency);
        out.put("duration_sec", req.durationSec);
        out.put("warmup", req.warmup);
        out.put("total_requests", total);
        out.put("success_requests", arr.length);
        out.put("error_requests", errors.get());
        out.put("allowed", allowed.get());
        out.put("denied", denied.get());
        out.put("elapsed_ms", elapsedNs / 1_000_000);
        out.put("rps", arr.length * 1000.0 / Math.max(1, elapsedNs / 1_000_000));
        out.put("latency_us", percentiles(arr));
        out.put("histogram", histogram(arr));
        if (withTimeline) {
            List<Map<String, Object>> tl = new ArrayList<>();
            for (int i = 0; i < durSec; i++) {
                long c = perSecCount[i].get();
                long ls = perSecLatSum[i].get();
                tl.add(Map.of(
                        "sec", i + 1,
                        "rps", c,
                        "avg_us", c > 0 ? ls / c / 1000 : 0
                ));
            }
            out.put("timeline", tl);
        }
        return out;
    }

    private Map<String, Object> percentiles(long[] sortedNs) {
        if (sortedNs.length == 0) return Map.of("count", 0);
        double avg = Arrays.stream(sortedNs).average().orElse(0);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("count", sortedNs.length);
        p.put("min", sortedNs[0] / 1000);
        p.put("max", sortedNs[sortedNs.length - 1] / 1000);
        p.put("avg", (long) (avg / 1000));
        p.put("p50", sortedNs[(int) (sortedNs.length * 0.50)] / 1000);
        p.put("p90", sortedNs[(int) (sortedNs.length * 0.90)] / 1000);
        p.put("p95", sortedNs[(int) (sortedNs.length * 0.95)] / 1000);
        p.put("p99", sortedNs[(int) (sortedNs.length * 0.99)] / 1000);
        p.put("p999", sortedNs[(int) Math.min(sortedNs.length - 1, sortedNs.length * 0.999)] / 1000);
        return p;
    }

    /** Log-style histogram buckets (microseconds upper bound). */
    private List<Map<String, Object>> histogram(long[] sortedNs) {
        long[] bucketUpperUs = { 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000, Long.MAX_VALUE };
        String[] labels = { "<50us", "50-100us", "100-200us", "200-500us", "500us-1ms", "1-2ms", "2-5ms", "5-10ms", "10-20ms", "20-50ms", "50-100ms", ">100ms" };
        long[] counts = new long[bucketUpperUs.length];
        int idx = 0;
        for (long ns : sortedNs) {
            long us = ns / 1000;
            while (idx < bucketUpperUs.length && us > bucketUpperUs[idx]) idx++;
            // back off if we overshot
            if (idx >= bucketUpperUs.length) idx = bucketUpperUs.length - 1;
            counts[idx]++;
        }
        // re-bucket properly (above is buggy because sortedNs is sorted but idx-only-forward depends on per-element)
        // recompute cleanly:
        Arrays.fill(counts, 0L);
        for (long ns : sortedNs) {
            long us = ns / 1000;
            for (int b = 0; b < bucketUpperUs.length; b++) {
                if (us <= bucketUpperUs[b]) { counts[b]++; break; }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            out.add(Map.of("label", labels[i], "upper_us", bucketUpperUs[i] == Long.MAX_VALUE ? -1 : bucketUpperUs[i], "count", counts[i]));
        }
        return out;
    }

    public static class PerfRunReq {
        public String systemCd = "ERP";
        public int durationSec = 5;
        public int concurrency = 50;
        public boolean warmup = true;
        public int userSample = 100;
        public int urlSample = 500;
    }

    public static class PerfSweepReq {
        public String systemCd = "ERP";
        public int durationSecPerLevel = 3;
        public boolean warmup = true;
        public List<Integer> concurrencyLevels = List.of(1, 10, 50, 100, 200);
    }
}
