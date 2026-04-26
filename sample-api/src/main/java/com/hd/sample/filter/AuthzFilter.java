package com.hd.sample.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AuthzFilter implements Filter {

    private final RestClient authzRestClient;

    @Value("${authz.enforce:true}") private boolean enforce;
    @Value("${authz.default.system-cd}") private String defSystem;
    @Value("${authz.default.company-cd}") private String defCompany;
    @Value("${authz.default.dept-id}") private String defDept;
    @Value("${authz.default.user-id}") private String defUser;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        HttpServletResponse hr = (HttpServletResponse) res;

        String path = http.getRequestURI();
        if (path.startsWith("/actuator") || path.equals("/")) {
            chain.doFilter(req, res);
            return;
        }

        if (!enforce) {
            chain.doFilter(req, res);
            return;
        }

        String system  = headerOrDefault(http, "X-System-Cd",  defSystem);
        String company = headerOrDefault(http, "X-Company-Cd", defCompany);
        String dept    = headerOrDefault(http, "X-Dept-Id",    defDept);
        String user    = headerOrDefault(http, "X-User-Id",    defUser);

        try {
            Map<?, ?> resp = authzRestClient.get()
                    .uri(uri -> uri.path("/api/v1/authz/check")
                            .queryParam("system_cd", system)
                            .queryParam("company_cd", company)
                            .queryParam("dept_id", dept)
                            .queryParam("user_id", user)
                            .queryParam("method", http.getMethod())
                            .queryParam("api_url", path)
                            .build())
                    .retrieve()
                    .body(Map.class);

            boolean allowed = resp != null && Boolean.TRUE.equals(resp.get("allowed"));
            hr.setHeader("X-Authz-Allowed", String.valueOf(allowed));
            if (!allowed) {
                hr.setStatus(HttpStatus.FORBIDDEN.value());
                hr.setContentType("application/json");
                hr.getWriter().write("{\"error\":\"forbidden\",\"path\":\"" + path + "\"}");
                return;
            }
        } catch (Exception e) {
            log.warn("Authz call failed: {}", e.toString());
            hr.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            hr.setContentType("application/json");
            hr.getWriter().write("{\"error\":\"authz_unreachable\"}");
            return;
        }

        chain.doFilter(req, res);
    }

    private static String headerOrDefault(HttpServletRequest req, String name, String dflt) {
        String v = req.getHeader(name);
        return v == null || v.isBlank() ? dflt : v;
    }
}
