package com.hd.sample.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Catch-all stub. Acts as the back-end for all 1,000 seeded API URLs.
 * Real method/path are reflected in the response so callers can see authz worked end-to-end.
 */
@RestController
public class CatchAllController {

    @RequestMapping("/api/**")
    public Map<String, Object> catchAll(HttpServletRequest req) {
        return Map.of(
                "ok", true,
                "method", req.getMethod(),
                "path", req.getRequestURI(),
                "user", req.getHeader("X-User-Id"),
                "timestamp", Instant.now().toString()
        );
    }

    @RequestMapping("/")
    public Map<String, Object> root() {
        return Map.of("service", "sample-api", "endpoints", "1000+ /api/**");
    }
}
