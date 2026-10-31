package com.aurora.observation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer apiCorsConfigurer(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        // Accept "a, b" as well as "a,b" in the environment variable.
        String[] origins = parseOrigins(allowedOrigins);
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .maxAge(3600);
            }
        };
    }

    static String[] parseOrigins(String allowedOrigins) {
        return java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toArray(String[]::new);
    }
}
