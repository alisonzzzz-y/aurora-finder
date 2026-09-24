package com.aurora.observation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ProviderHttpClientConfig {
    @Bean
    public HttpClient providerHttpClient(
            @Value("${app.http.connect-timeout:3s}") Duration connectTimeout) {
        return HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }
}
