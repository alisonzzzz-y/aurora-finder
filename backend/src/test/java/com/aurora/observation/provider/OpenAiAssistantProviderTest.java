package com.aurora.observation.provider;

import com.aurora.observation.service.AssistantUnavailableException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiAssistantProviderTest {
    @Test
    void refusesRequestsWhenApiKeyIsNotConfigured() {
        ObjectMapper mapper = new ObjectMapper();
        OpenAiAssistantProvider provider = new OpenAiAssistantProvider(
                HttpClient.newHttpClient(), mapper, " ", "gpt-6-luna",
                "https://api.openai.com/v1", Duration.ofSeconds(1));

        assertThrows(AssistantUnavailableException.class, () -> provider.respond(
                "instructions", mapper.createArrayNode(), mapper.createArrayNode()));
    }

    @Test
    void returnsUsageDataAndAcceptsAValidResponsesApiResult() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"resp_test\",\"usage\":{\"input_tokens\":45,\"input_tokens_details\":{\"cached_tokens\":10},\"output_tokens\":12}}");
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("x-request-id", List.of("req_test")),
                (name, value) -> true));
        when(httpClient.send(any(java.net.http.HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        OpenAiAssistantProvider provider = new OpenAiAssistantProvider(
                httpClient, mapper, "test-key", "gpt-6-luna", "https://api.openai.com/v1", Duration.ofSeconds(1));

        var result = provider.respond("instructions", mapper.createArrayNode(), mapper.createArrayNode());

        assertEquals("resp_test", result.path("id").asText());
        assertEquals(45, result.path("usage").path("input_tokens").asInt());
        assertEquals(10, result.path("usage").path("input_tokens_details").path("cached_tokens").asInt());
        assertEquals(12, result.path("usage").path("output_tokens").asInt());
    }
}
