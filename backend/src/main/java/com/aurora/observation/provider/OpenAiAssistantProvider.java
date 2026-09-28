package com.aurora.observation.provider;

import com.aurora.observation.service.AssistantRateLimitException;
import com.aurora.observation.service.AssistantUnavailableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class OpenAiAssistantProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAiAssistantProvider.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final URI responsesUri;
    private final Duration requestTimeout;

    public OpenAiAssistantProvider(HttpClient httpClient, ObjectMapper objectMapper,
                                   @Value("${app.openai.api-key:}") String apiKey,
                                   @Value("${app.openai.model:gpt-6-luna}") String model,
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
        long startedAtNanos = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = elapsedMillis(startedAtNanos);
            String requestId = requestId(response);
            if (response.statusCode() == 429) {
                log.warn("OpenAI Responses API rate limited the request (model={}, durationMs={}, requestId={}).",
                        model, durationMs, requestId);
                throw new AssistantRateLimitException();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenAI Responses API returned HTTP {} (model={}, durationMs={}, requestId={}).",
                        response.statusCode(), model, durationMs, requestId);
                throw new AssistantUnavailableException("The AI service returned HTTP " + response.statusCode() + ".");
            }
            try {
                JsonNode result = objectMapper.readTree(response.body());
                JsonNode usage = result.path("usage");
                log.info("OpenAI Responses API completed (model={}, durationMs={}, inputTokens={}, cachedInputTokens={}, outputTokens={}, requestId={}).",
                        model, durationMs, tokenCount(usage, "input_tokens"),
                        tokenCount(usage.path("input_tokens_details"), "cached_tokens"),
                        tokenCount(usage, "output_tokens"), requestId);
                return result;
            } catch (RuntimeException error) {
                log.error("OpenAI Responses API returned invalid JSON (model={}, durationMs={}, requestId={}).",
                        model, durationMs, requestId, error);
                throw new AssistantUnavailableException("The AI service returned an invalid response.", error);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.warn("OpenAI Responses API request was interrupted (model={}, durationMs={}).",
                    model, elapsedMillis(startedAtNanos));
            throw new AssistantUnavailableException("The AI request was interrupted.", error);
        } catch (IOException | IllegalArgumentException error) {
            log.warn("OpenAI Responses API request failed before a response was received (model={}, durationMs={}, failureType={}).",
                    model, elapsedMillis(startedAtNanos), error.getClass().getSimpleName());
            throw new AssistantUnavailableException("The AI service could not be reached.", error);
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }

    private String requestId(HttpResponse<?> response) {
        return response.headers().firstValue("x-request-id").orElse("unknown");
    }

    private String tokenCount(JsonNode usage, String field) {
        JsonNode count = usage.path(field);
        return count.isIntegralNumber() ? count.asText() : "unknown";
    }

    public String model() {
        return model;
    }
}
