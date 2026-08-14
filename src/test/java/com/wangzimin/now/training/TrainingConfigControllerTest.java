package com.wangzimin.now.training;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class TrainingConfigControllerTest {

    @Test
    void readsAndWritesOnlyForAuthenticatedSubject() {
        TrainingConfigService service = mock(TrainingConfigService.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("27");
        var expected = new TrainingConfigService.TrainingConfigResponse("free", null, null, null, 0, true);
        var request = new TrainingConfigService.TrainingConfigRequest("free", null, Instant.now());
        when(service.get(27L)).thenReturn(expected);
        when(service.save(27L, request)).thenReturn(expected);

        TrainingConfigController controller = new TrainingConfigController(service);

        assertSame(expected, controller.get(jwt));
        assertSame(expected, controller.save(jwt, request));
        verify(service).get(27L);
        verify(service).save(27L, request);
    }
}
