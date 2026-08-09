package com.wangzimin.now.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void returnsApplicationHealthWithoutDatabase() {
        HealthController.HealthResponse response = new HealthController().health();

        assertEquals("UP", response.status());
        assertEquals("now-server", response.service());
        assertNotNull(response.timestamp());
    }
}
