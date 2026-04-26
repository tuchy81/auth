package com.hd.authz.api;

import com.hd.authz.service.BulkGrantService;
import com.hd.authz.service.EffectivePermService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EffectiveAndBulkController {

    private final EffectivePermService effectiveService;
    private final BulkGrantService bulkService;

    @GetMapping("/permissions/effective/by-user")
    public Map<String, Object> effective(@RequestParam("system_cd") String systemCd,
                                         @RequestParam("user_id") String userId) {
        return effectiveService.forUser(systemCd, userId);
    }

    @PostMapping("/permissions/bulk/preview")
    public Map<String, Object> bulkPreview(@RequestBody BulkGrantService.BulkGrantReq req) {
        return bulkService.previewBulk(req);
    }

    @PostMapping("/permissions/bulk")
    public Map<String, Object> bulkApply(@RequestBody BulkGrantService.BulkGrantReq req,
                                         @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
        return bulkService.applyBulk(req, actor);
    }
}
