package com.aurora.observation.provider;

import com.aurora.observation.service.AssistantUnavailableException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiAssistantProviderTest {
    @Test
    void refusesRequestsWhenApiKeyIsNotConfigured() {
        ObjectMapper mapper = new ObjectMapper();
        OpenAiAssistantProvider provider = new OpenAiAssistantProvider(
                HttpClient.newHttpClient(), mapper, " ", "gpt-5-mini",
                "https://api.openai.com/v1", Duration.ofSeconds(1));

        assertThrows(AssistantUnavailableException.class, () -> provider.respond(
                "instructions", mapper.createArrayNode(), mapper.createArrayNode()));
    }
}
