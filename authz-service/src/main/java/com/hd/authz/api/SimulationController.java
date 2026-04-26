package com.hd.authz.api;

import com.hd.authz.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/authz/simulate")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService sim;

    @PostMapping("/grant")
    public Map<String, Object> grant(@RequestBody Map<String, Object> req) { return sim.simulateGrant(req); }

    @PostMapping("/revoke")
    public Map<String, Object> revoke(@RequestBody Map<String, Object> req) { return sim.simulateRevoke(req); }
}
