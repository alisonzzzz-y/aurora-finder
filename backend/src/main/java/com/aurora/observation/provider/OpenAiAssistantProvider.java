package com.aurora.observation.provider;

import com.aurora.observation.service.AssistantRateLimitException;
import com.aurora.observation.service.AssistantUnavailableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class OpenAiAssistantProvider {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final URI responsesUri;
    private final Duration requestTimeout;

    public OpenAiAssistantProvider(HttpClient httpClient, ObjectMapper objectMapper,
                                   @Value("${app.openai.api-key:}") String apiKey,
                                   @Value("${app.openai.model:gpt-5-mini}") String model,
                                   @Value("${app.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                                   @Value("${app.openai.request-timeout:35s}") Duration requestTimeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey.trim();
        this.model = model.trim();
        this.responsesUri = URI.create(baseUrl.replaceAll("/+$", "") + "/responses");
        this.requestTimeout = requestTimeout;
    }

    public JsonNode respond(String instructions, ArrayNode input, ArrayNode tools) {
        if (apiKey.isBlank()) {
            throw new AssistantUnavailableException("The AI assistant is not configured yet.");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("instructions", instructions);
        payload.set("input", input);
        payload.set("tools", tools);
        payload.put("tool_choice", "auto");
        payload.put("parallel_tool_calls", false);
        payload.put("store", false);
        payload.put("max_output_tokens", 1200);

        HttpRequest request = HttpRequest.newBuilder(responsesUri)
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new AssistantRateLimitException();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AssistantUnavailableException("The AI service returned HTTP " + response.statusCode() + ".");
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssistantUnavailableException("The AI request was interrupted.", error);
        } catch (IOException | IllegalArgumentException error) {
            throw new AssistantUnavailableException("The AI service could not be reached.", error);
        }
    }

    public String model() {
        return model;
    }
}
