package com.wangzimin.now.training;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/training-config")
public class TrainingConfigController {

    private final TrainingConfigService service;

    public TrainingConfigController(TrainingConfigService service) {
        this.service = service;
    }

    @GetMapping
    public TrainingConfigService.TrainingConfigResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(Long.valueOf(jwt.getSubject()));
    }

    @PutMapping
    public TrainingConfigService.TrainingConfigResponse save(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TrainingConfigService.TrainingConfigRequest request) {
        return service.save(Long.valueOf(jwt.getSubject()), request);
    }
}
