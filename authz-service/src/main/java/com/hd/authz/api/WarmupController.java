package com.hd.authz.api;

import com.hd.authz.service.WarmupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/authz/warmup")
@RequiredArgsConstructor
public class WarmupController {

    private final WarmupService warmup;

    @PostMapping("/system")
    public Map<String, Object> system(@RequestBody Map<String, Object> body) {
        return warmup.warmupSystem((String) body.get("system_cd"), (String) body.get("user_id"));
    }

    @PostMapping("/menu")
    public Map<String, Object> menu(@RequestBody Map<String, Object> body) {
        return warmup.warmupMenu(
                (String) body.get("system_cd"),
                (String) body.get("user_id"),
                ((Number) body.get("menu_id")).longValue(),
                Boolean.TRUE.equals(body.getOrDefault("prefetch_children", false))
        );
    }
}
