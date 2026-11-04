package com.aurora.observation.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CorsConfigTest {
    @Test
    void trimsSpacesAndDropsEmptyOrigins() {
        assertArrayEquals(new String[]{"https://a.example", "https://b.example"},
                CorsConfig.parseOrigins(" https://a.example, https://b.example ,"));
    }
}
