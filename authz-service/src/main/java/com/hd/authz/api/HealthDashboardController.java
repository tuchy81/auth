package com.hd.authz.api;

import com.hd.authz.service.HealthDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthDashboardController {

    private final HealthDashboardService dashboard;

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return dashboard.snapshot();
    }
}
