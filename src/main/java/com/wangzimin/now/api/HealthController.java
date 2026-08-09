package com.wangzimin.now.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight application endpoint used by the mobile client and deployment checks. */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", "now-server", Instant.now());
    }

    public record HealthResponse(String status, String service, Instant timestamp) {
    }
}
