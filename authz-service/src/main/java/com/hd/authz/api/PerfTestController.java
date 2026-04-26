package com.hd.authz.api;

import com.hd.authz.service.PerfTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/perftest")
@RequiredArgsConstructor
public class PerfTestController {

    private final PerfTestService perf;

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody PerfTestService.PerfRunReq req) {
        return perf.run(req);
    }

    @PostMapping("/sweep")
    public Map<String, Object> sweep(@RequestBody PerfTestService.PerfSweepReq req) {
        return perf.sweep(req);
    }
}
