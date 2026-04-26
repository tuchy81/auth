package com.hd.authz.api;

import com.hd.authz.service.PropagationTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/proptest")
@RequiredArgsConstructor
public class PropagationTestController {

    private final PropagationTestService service;

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody PropagationTestService.PropRunReq req) {
        return service.run(req);
    }
}
