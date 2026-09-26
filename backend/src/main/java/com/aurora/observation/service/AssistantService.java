package com.aurora.observation.service;

import com.aurora.observation.dto.AssistantChatRequest;
import com.aurora.observation.dto.AssistantChatResponse;
import com.aurora.observation.dto.AssistantMessage;
import com.aurora.observation.provider.OpenAiAssistantProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AssistantService {
    private static final int MAX_MODEL_TURNS = 5;
    private static final String INSTRUCTIONS = """
            You are the read-only assistant inside Aurora Finder. Answer in the user's requested language.
            Use the provided tools for current aurora, geomagnetic, cloud, darkness, location, and source facts.
            Do not rely on remembered forecast data. Never invent observations, dates, source status, or a person's
            probability of seeing aurora. The OVATION value is a model signal, not a calibrated viewing probability.
            Explain uncertainty plainly. Distinguish global geomagnetic activity from local viewing conditions.
            When a place name is ambiguous, show the matching candidates and ask the user to choose one before using
            a location ID. Use local dates and include the UTC offset when the tool data provides it. Keep answers
            concise and practical. Mention unavailable or incomplete source data instead of filling gaps.
            """;

    private final OpenAiAssistantProvider openAi;
    private final ObservationToolsService tools;
    private final ObjectMapper objectMapper;
    private final ArrayNode toolDefinitions;

    public AssistantService(OpenAiAssistantProvider openAi, ObservationToolsService tools, ObjectMapper objectMapper) {
        this.openAi = openAi;
        this.tools = tools;
        this.objectMapper = objectMapper;
        this.toolDefinitions = buildToolDefinitions();
    }

    public AssistantChatResponse chat(AssistantChatRequest request) {
        ArrayNode input = objectMapper.createArrayNode();
        List<AssistantMessage> history = request.history() == null ? List.of() : request.history();
        history.forEach(item -> input.add(message(item.role(), item.content())));

        StringBuilder userMessage = new StringBuilder(request.message().trim());
        userMessage.append("\n\nRequested answer language: ")
                .append("zh".equalsIgnoreCase(request.language()) ? "Simplified Chinese" : "English");
        if (request.locationId() != null && request.locationId() > 0) {
            userMessage.append("\nSelected Aurora Finder location ID: ").append(request.locationId());
        }
        input.add(message("user", userMessage.toString()));

        for (int turn = 0; turn < MAX_MODEL_TURNS; turn++) {
            JsonNode response = openAi.respond(INSTRUCTIONS, input, toolDefinitions);
            JsonNode output = response.path("output");
            if (!output.isArray()) {
                throw new AssistantUnavailableException("The AI service returned an invalid response.");
            }

            boolean calledTool = false;
            for (JsonNode item : output) {
                input.add(item.deepCopy());
                if ("function_call".equals(item.path("type").asText())) {
                    calledTool = true;
                    input.add(toolOutput(item));
                }
            }
            if (!calledTool) {
                String answer = extractText(output);
                if (answer.isBlank()) {
                    throw new AssistantUnavailableException("The AI service returned no answer.");
                }
                return new AssistantChatResponse(answer, openAi.model());
            }
        }
        throw new AssistantUnavailableException("The AI assistant used too many tool steps.");
    }

    private ObjectNode toolOutput(JsonNode call) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("type", "function_call_output");
        output.put("call_id", call.path("call_id").asText());
        try {
            JsonNode arguments = objectMapper.readTree(call.path("arguments").asText("{}"));
            output.put("output", objectMapper.writeValueAsString(runTool(call.path("name").asText(), arguments)));
        } catch (RuntimeException error) {
            ObjectNode failure = objectMapper.createObjectNode();
            failure.put("status", "unavailable");
            failure.put("message", safeToolError(error));
            output.put("output", failure.toString());
        }
        return output;
    }

    private Object runTool(String name, JsonNode arguments) {
        return switch (name) {
            case "search_places" -> tools.searchPlaces(requiredText(arguments, "query"));
            case "get_local_night_facts" -> tools.getLocalNightFacts(requiredPositiveLong(arguments, "location_id"));
            case "get_night_outlook" -> tools.getNightOutlook(
                    requiredPositiveLong(arguments, "location_id"),
                    LocalDate.parse(requiredText(arguments, "local_date")));
            case "get_global_kp_forecast" -> tools.getGlobalKpForecast();
            case "get_three_day_storm_forecast" -> tools.getThreeDayStormForecast();
            case "get_active_geomagnetic_warnings" -> tools.getActiveGeomagneticWarnings();
            default -> throw new IllegalArgumentException("Unknown read-only tool.");
        };
    }

    private String extractText(JsonNode output) {
        StringBuilder text = new StringBuilder();
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    if (!text.isEmpty()) text.append('\n');
                    text.append(content.path("text").asText());
                }
            }
        }
        return text.toString().trim();
    }

    private ObjectNode message(String role, String content) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("role", role);
        result.put("content", content);
        return result;
    }

    private String requiredText(JsonNode arguments, String field) {
        String value = arguments.path(field).asText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Missing tool argument: " + field);
        return value;
    }

    private long requiredPositiveLong(JsonNode arguments, String field) {
        long value = arguments.path(field).asLong(-1);
        if (value <= 0) throw new IllegalArgumentException("Invalid tool argument: " + field);
        return value;
    }

    private String safeToolError(Exception error) {
        if (error instanceof LocationNotFoundException) return "The selected location was not found.";
        if (error instanceof UnsupportedNightDateException unsupported) {
            return "That local date is outside the available window. Available dates: " + unsupported.availableDates();
        }
        return "The requested source data is currently unavailable.";
    }

    private ArrayNode buildToolDefinitions() {
        ArrayNode definitions = objectMapper.createArrayNode();
        definitions.add(tool("search_places", "Search matching places before selecting a location ID.",
                properties("query", "string", "City or place name"), List.of("query")));
        definitions.add(tool("get_local_night_facts", "Get source-backed aurora, cloud, darkness, and outlook facts for a selected location ID.",
                properties("location_id", "integer", "Selected provider location ID"), List.of("location_id")));

        ObjectNode nightProperties = objectMapper.createObjectNode();
        nightProperties.set("location_id", property("integer", "Selected provider location ID"));
        nightProperties.set("local_date", property("string", "Local calendar date in YYYY-MM-DD format"));
        definitions.add(tool("get_night_outlook", "Get one supported local night outlook by location ID and local date.",
                nightProperties, List.of("location_id", "local_date")));
        definitions.add(tool("get_global_kp_forecast", "Get the latest source-backed global Kp forecast.",
                objectMapper.createObjectNode(), List.of()));
        definitions.add(tool("get_three_day_storm_forecast", "Get the latest three-day geomagnetic storm forecast.",
                objectMapper.createObjectNode(), List.of()));
        definitions.add(tool("get_active_geomagnetic_warnings", "Get active geomagnetic watches and warnings.",
                objectMapper.createObjectNode(), List.of()));
        return definitions;
    }

    private ObjectNode tool(String name, String description, ObjectNode properties, List<String> required) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "function");
        tool.put("name", name);
        tool.put("description", description);
        tool.put("strict", true);
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("type", "object");
        parameters.set("properties", properties);
        parameters.set("required", objectMapper.valueToTree(required));
        parameters.put("additionalProperties", false);
        tool.set("parameters", parameters);
        return tool;
    }

    private ObjectNode properties(String name, String type, String description) {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set(name, property(type, description));
        return properties;
    }

    private ObjectNode property(String type, String description) {
        ObjectNode property = objectMapper.createObjectNode();
        property.put("type", type);
        property.put("description", description);
        return property;
    }
}
