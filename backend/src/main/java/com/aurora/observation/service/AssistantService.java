package com.aurora.observation.service;

import com.aurora.observation.dto.AssistantChatRequest;
import com.aurora.observation.dto.AssistantChatResponse;
import com.aurora.observation.dto.AssistantMessage;
import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.NightOutlook;
import com.aurora.observation.dto.ObservationFactsResponse;
import com.aurora.observation.dto.WeatherCloudPoint;
import com.aurora.observation.provider.OpenAiAssistantProvider;
import com.aurora.observation.record.NoopRunRecordStore;
import com.aurora.observation.record.RunRecordStore;
import com.aurora.observation.record.RunRecordUnavailableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AssistantService {
    private static final int MAX_MODEL_TURNS = 5;
    private static final Pattern HOURLY_CLOUD_PERCENT = Pattern.compile(
            "(?m)(?<!\\d)((?:[01]?\\d|2[0-3])):([0-5]\\d)[^\\n%]{0,60}?([0-9]+(?:\\.[0-9]+)?)\\s*%");
    private static final Pattern CORRECTION_WORD = Pattern.compile(
            "(?i)\\b(?:correction|corrected|actually|i mean|to correct)\\b|更正|修正|改为|应为|更准确地说");
    private static final Pattern CLOUD_CONTEXT = Pattern.compile("(?i)cloud|云量|云层|云覆盖");
    private static final String INSTRUCTIONS = """
            You are the read-only assistant inside Aurora Finder. Answer in the user's requested language.
            Use the provided tools for current aurora, geomagnetic, cloud, darkness, location, and source facts.
            Do not rely on remembered forecast data. Never invent observations, dates, source status, or a person's
            probability of seeing aurora. The OVATION value is a model signal, not a calibrated viewing probability.
            For questions about how often aurora has historically been visible in a city or region, state that this
            app has no validated local recurrence dataset and cannot give a reliable interval. Never estimate that
            frequency from latitude, Kp, current OVATION values, or isolated community reports. Offer to check a
            supported current or near-term forecast instead.
            The viewingConditions field is an experimental short-range conditions rating, not a probability.
            If reporting its HIGH/MEDIUM/LOW level, give evaluatedAtUtc and state the rule is uncalibrated.
            Never turn a rating or model signal into a viewing percentage or extend it to a whole night.
            Cloud forecasts now cover the remaining current hours and three local nights; select the user's
            requested local date before summarising cloud values. Missing future aurora data stays missing.
            Explain uncertainty plainly. Distinguish global geomagnetic activity from local viewing conditions.
            Search for a place before using local tools unless the application supplied a selected location ID.
            Use only an application-selected location ID, a unique candidate returned by search_places, or a
            location explicitly selected by the application from the previous turn's candidate list. If multiple
            candidates match, show them and ask the user to choose; do not query any candidate until the user has
            clarified. For local dates, call get_local_night_facts first and use only the exact
            localDate values returned for that place. Map tonight/tomorrow to a returned date only when unambiguous;
            otherwise ask the user to choose a date. Never guess a date or use the server's date. Include the place's
            UTC offset when tool data provides it. Keep answers concise and practical. Mention unavailable or
            incomplete source data instead of filling gaps.
            For cloud cover, use the returned percentages and local hours. When summarizing an interval with a
            numeric range, include its lowest and highest available values; do not omit hourly dips or peaks to
            make the trend smoother. Prefer a few exact hour/value examples when the interval is not clearly defined.
            Before finishing an hourly cloud summary, check that you have not assigned conflicting percentages to
            the same local hour. Do not append a correction that contradicts an earlier value; if you cannot give
            a consistent summary, say so and direct the user to the local cloud forecast chart.
            """;

    private final OpenAiAssistantProvider openAi;
    private final ObservationToolsService tools;
    private final ObjectMapper objectMapper;
    private final ArrayNode toolDefinitions;
    private final RunRecordStore records;

    public AssistantService(OpenAiAssistantProvider openAi, ObservationToolsService tools, ObjectMapper objectMapper) {
        this(openAi, tools, objectMapper, new NoopRunRecordStore());
    }

    @Autowired
    public AssistantService(OpenAiAssistantProvider openAi, ObservationToolsService tools,
                            ObjectMapper objectMapper, RunRecordStore records) {
        this.openAi = openAi;
        this.tools = tools;
        this.objectMapper = objectMapper;
        this.records = records;
        this.toolDefinitions = buildToolDefinitions();
    }

    public AssistantChatResponse chat(AssistantChatRequest request) {
        String runId = records.begin("ASSISTANT", request.locationId());
        try {
            AssistantChatResponse answer = answer(request, runId);
            records.finish(runId, "COMPLETED");
            return new AssistantChatResponse(answer.answer(), answer.model(), answer.locationCandidates(), runId);
        } catch (RuntimeException failure) {
            records.finish(runId, "FAILED");
            throw failure;
        }
    }

    private AssistantChatResponse answer(AssistantChatRequest request, String runId) {
        ArrayNode input = objectMapper.createArrayNode();
        List<AssistantMessage> history = request.history() == null ? List.of() : request.history();
        history.forEach(item -> input.add(message(item.role(), item.content())));

        StringBuilder userMessage = new StringBuilder(request.message().trim());
        userMessage.append("\n\nRequested answer language: ")
                .append("zh".equalsIgnoreCase(request.language()) ? "Simplified Chinese" : "English");
        if (request.locationId() != null && request.locationId() > 0) {
            userMessage.append("\nSelected Aurora Finder location ID: ").append(request.locationId());
        }
        ToolContext toolContext = new ToolContext();
        toolContext.runId = runId;
        if (request.locationId() != null && request.locationId() > 0) {
            toolContext.permittedLocationIds.add(request.locationId());
        } else {
            toolContext.latestCandidates = lastLocationCandidates(history);
            if (!toolContext.latestCandidates.isEmpty()) {
                Location selected = resolveCandidate(request.message(), toolContext.latestCandidates);
                if (selected == null) {
                    return new AssistantChatResponse(selectionPrompt(toolContext.latestCandidates,
                            "zh".equalsIgnoreCase(request.language())), openAi.model(), toolContext.latestCandidates);
                }
                toolContext.permittedLocationIds.add(selected.id());
                toolContext.latestCandidates = List.of();
                userMessage.append("\nUser selected this matching location: ")
                        .append(locationDescription(selected)).append(" (location ID ").append(selected.id()).append(").");
            }
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
                    input.add(toolOutput(item, toolContext));
                }
            }
            if (!calledTool) {
                String answer = extractText(output);
                if (answer.isBlank()) {
                    throw new AssistantUnavailableException("The AI service returned no answer.");
                }
                List<Location> choices = toolContext.latestCandidates.size() > 1
                        ? toolContext.latestCandidates : List.of();
                if (hasConflictingCorrectedCloudValues(answer)) {
                    answer = inconsistentCloudAnswer("zh".equalsIgnoreCase(request.language())) + sourceCloudExamples(toolContext, "zh".equalsIgnoreCase(request.language()));
                } else if (hasUnsupportedHourlyCloudValues(answer, toolContext)) {
                    answer = unverifiedCloudAnswer("zh".equalsIgnoreCase(request.language())) + sourceCloudExamples(toolContext, "zh".equalsIgnoreCase(request.language()));
                }
                return new AssistantChatResponse(answer, openAi.model(), choices);
            }
        }
        throw new AssistantUnavailableException("The AI assistant used too many tool steps.");
    }

    private boolean hasConflictingCorrectedCloudValues(String answer) {
        if (!CORRECTION_WORD.matcher(answer).find()) return false;

        Map<String, String> percentagesByHour = new HashMap<>();
        Matcher matcher = HOURLY_CLOUD_PERCENT.matcher(answer);
        while (matcher.find()) {
            String hour = String.format(Locale.ROOT, "%02d:%s", Integer.parseInt(matcher.group(1)), matcher.group(2));
            String percentage = new BigDecimal(matcher.group(3)).stripTrailingZeros().toPlainString();
            String previous = percentagesByHour.putIfAbsent(hour, percentage);
            if (previous != null && !previous.equals(percentage)) return true;
        }
        return false;
    }

    private String inconsistentCloudAnswer(boolean chinese) {
        return chinese
                ? "这次回复中的逐小时云量数值前后不一致，我不想给你错误数据。请查看页面里的当地云量预报图，或稍后重试。"
                : "I couldn't provide a consistent hourly cloud summary, so I won't guess. Please check the local cloud forecast chart or try again later.";
    }

    private boolean hasUnsupportedHourlyCloudValues(String answer, ToolContext context) {
        if (!context.cloudFactsRequested || !CLOUD_CONTEXT.matcher(answer).find()) return false;
        Matcher matcher = HOURLY_CLOUD_PERCENT.matcher(answer);
        while (matcher.find()) {
            String hour = String.format(Locale.ROOT, "%02d:%s", Integer.parseInt(matcher.group(1)), matcher.group(2));
            Set<BigDecimal> sourceValues = context.cloudPercentagesByLocalHour.get(hour);
            if (sourceValues == null || sourceValues.isEmpty()) return true;

            BigDecimal stated = new BigDecimal(matcher.group(3));
            boolean matchesSource = sourceValues.stream().anyMatch(source ->
                    source.subtract(stated).abs().compareTo(roundingTolerance(stated)) <= 0);
            if (!matchesSource) return true;
        }
        return false;
    }

    private BigDecimal roundingTolerance(BigDecimal stated) {
        return stated.stripTrailingZeros().scale() <= 0
                ? new BigDecimal("0.6")
                : new BigDecimal("0.15");
    }

    private String unverifiedCloudAnswer(boolean chinese) {
        return chinese
                ? "这次回复中的小时云量数值无法与天气来源数据核对。为避免误报，请查看页面里的当地云量预报图。"
                : "I couldn't verify the hourly cloud values against the weather source. To avoid giving you inaccurate figures, please check the local cloud forecast chart.";
    }

    private String sourceCloudExamples(ToolContext context, boolean chinese) {
        ObservationFactsResponse facts = context.latestCloudFacts;
        if (facts == null || facts.cloudForecast().status() != com.aurora.observation.dto.FactFetchStatus.CURRENT) return "";
        Location location = facts.outlook().location();
        ZoneId zone = ZoneId.of(location.timezone());
        var formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm XXX", Locale.ROOT);
        var points = facts.cloudForecast().data().cloudForecast().stream()
                .filter(point -> point != null && point.validAt() != null && point.cloudCoverPercent() != null)
                .sorted(java.util.Comparator.comparing(WeatherCloudPoint::validAt)).limit(2).toList();
        if (points.isEmpty()) return "";
        StringBuilder summary = new StringBuilder(chinese
                ? "\n\n以下为天气来源的样本读数（当地时间，不代表整晚）：\n"
                : "\n\nSample readings from the weather source, in local time. They do not describe the whole night:\n");
        summary.append(location.name()).append(", ").append(location.country()).append("\n");
        for (WeatherCloudPoint point : points) {
            summary.append("- ").append(formatter.format(point.validAt().atZone(zone))).append(": ")
                    .append(BigDecimal.valueOf(point.cloudCoverPercent()).stripTrailingZeros().toPlainString()).append("%\n");
        }
        summary.append(chinese ? "数据来源：" : "Source: ").append(facts.cloudForecast().source());
        if (facts.cloudForecast().sourceUrl() != null) summary.append("\n").append(facts.cloudForecast().sourceUrl());
        return summary.toString();
    }

    private List<Location> lastLocationCandidates(List<AssistantMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            AssistantMessage message = history.get(i);
            if ("user".equals(message.role())) {
                break;
            }
            if ("assistant".equals(message.role()) && message.locationCandidates() != null
                    && message.locationCandidates().size() > 1) {
                return message.locationCandidates();
            }
        }
        return List.of();
    }

    private Location resolveCandidate(String reply, List<Location> candidates) {
        String value = normalize(reply);
        if (value.matches("[1-9][0-9]*")) {
            try {
                int index = Integer.parseInt(value) - 1;
                return index < candidates.size() ? candidates.get(index) : null;
            } catch (NumberFormatException invalidSelection) {
                return null;
            }
        }

        List<Location> fullLocationMatches = candidates.stream()
                .filter(candidate -> {
                    String description = normalize(locationDescription(candidate));
                    return !description.isEmpty() && (value.equals(description) || value.contains(description));
                }).toList();
        if (fullLocationMatches.size() == 1) return fullLocationMatches.getFirst();

        String translatedBuilder = value;
        if (value.contains("都柏林机场")) translatedBuilder += " dublin airport";
        if (value.contains("南都柏林")) translatedBuilder += " south dublin";
        if (value.contains("都柏林市")) translatedBuilder += " dublin city";
        if (value.contains("科克郡")) translatedBuilder += " cork";
        final String translated = translatedBuilder;

        String country = countryMention(value);
        List<Location> narrowed = country == null ? candidates : candidates.stream()
                .filter(candidate -> normalize(candidate.country()).equals(country)).toList();
        if (country != null && narrowed.size() == 1) return narrowed.getFirst();

        List<Location> exact = narrowed.stream().filter(candidate -> {
            String name = normalize(candidate.name());
            String query = normalize(translated);
            return !name.isEmpty() && (query.equals(name) || query.contains(name + " ")
                    || query.endsWith(" " + name));
        }).toList();
        if (exact.size() == 1) return exact.getFirst();
        if (exact.size() > 1) narrowed = exact;

        List<Location> qualified = narrowed.stream()
                .filter(candidate -> allDistinctiveWordsPresent(translated, candidate)).toList();
        return qualified.size() == 1 ? qualified.getFirst() : null;
    }

    private boolean allDistinctiveWordsPresent(String reply, Location candidate) {
        String normalizedReply = normalize(reply);
        for (String field : new String[]{candidate.region(), candidate.subregion(), candidate.country()}) {
            String normalizedField = normalize(field);
            if (normalizedField.length() > 3 && normalizedReply.contains(normalizedField)) return true;
        }
        return false;
    }

    private String countryMention(String value) {
        if (value.contains("ireland") || value.contains("爱尔兰")) return "ireland";
        if (value.contains("united states") || value.contains("usa") || value.contains("美国")) return "united states";
        if (value.contains("new zealand") || value.contains("新西兰")) return "new zealand";
        if (value.contains("canada") || value.contains("加拿大")) return "canada";
        return null;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private String locationDescription(Location location) {
        return java.util.Arrays.stream(new String[]{location.name(), location.region(), location.subregion(), location.country()})
                .filter(value -> value != null && !value.isBlank()).distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String selectionPrompt(List<Location> candidates, boolean chinese) {
        String options = java.util.stream.IntStream.range(0, candidates.size())
                .mapToObj(index -> (index + 1) + ". " + locationDescription(candidates.get(index)))
                .collect(java.util.stream.Collectors.joining("\n"));
        return chinese
                ? "我找到几个匹配地点，请回复序号或更具体的地点名称：\n" + options
                : "I found several matching places. Reply with a number or a more specific place name:\n" + options;
    }

    private ObjectNode toolOutput(JsonNode call, ToolContext context) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("type", "function_call_output");
        output.put("call_id", call.path("call_id").asText());
        try {
            JsonNode arguments = objectMapper.readTree(call.path("arguments").asText("{}"));
            output.put("output", objectMapper.writeValueAsString(
                    runTool(call.path("name").asText(), arguments, context)));
        } catch (LocationSelectionRequiredException error) {
            ObjectNode selection = objectMapper.createObjectNode();
            selection.put("status", "location_selection_required");
            selection.put("message", "Ask the user to choose a matching place before requesting local facts.");
            selection.set("candidates", objectMapper.valueToTree(error.candidates));
            output.put("output", selection.toString());
        } catch (LocalFactsRequiredException error) {
            ObjectNode failure = objectMapper.createObjectNode();
            failure.put("status", "local_facts_required");
            failure.put("message", "Read local night facts first and use one of the exact localDate values returned.");
            output.put("output", failure.toString());
        } catch (RunRecordUnavailableException error) {
            throw error;
        } catch (RuntimeException error) {
            ObjectNode failure = objectMapper.createObjectNode();
            failure.put("status", "unavailable");
            failure.put("message", safeToolError(error));
            output.put("output", failure.toString());
        }
        recordToolEvidence(call, output, context);
        return output;
    }

    private void recordToolEvidence(JsonNode call, ObjectNode output, ToolContext context) {
        String name = call.path("name").asText();
        ObjectNode evidence = objectMapper.createObjectNode();
        JsonNode args;
        JsonNode result;
        try {
            args = objectMapper.readTree(call.path("arguments").asText("{}"));
            result = objectMapper.readTree(output.path("output").asText("{}"));
        } catch (RuntimeException invalidJson) {
            args = objectMapper.createObjectNode();
            result = objectMapper.createObjectNode();
        }
        if (args.path("location_id").isIntegralNumber()) evidence.put("locationId", args.path("location_id").asLong());
        if (args.path("local_date").isTextual()) evidence.put("localDate", args.path("local_date").asText());
        if ("search_places".equals(name) && result.isArray()) {
            evidence.put("candidateCount", result.size());
            ArrayNode ids = evidence.putArray("candidateIds");
            result.forEach(place -> ids.add(place.path("id").asLong()));
        }
        if ("get_local_night_facts".equals(name)) {
            evidence.put("sourceStatus", result.path("sourceStatus").asText("unavailable"));
        }
        if ("get_night_outlook".equals(name)) {
            evidence.put("level", result.path("level").asText("unavailable"));
            evidence.put("reasonCode", result.path("reasonCode").asText("unavailable"));
        }
        if (result.isObject() && result.has("retrievedAt")) {
            evidence.put("retrievedAt", result.path("retrievedAt").asText());
        }
        String outcome = result.isObject() && result.has("status")
                ? result.path("status").asText("unavailable") : "OK";
        records.recordTool(context.runId, ++context.toolSequence, name, outcome, evidence.toString());
    }

    private Object runTool(String name, JsonNode arguments, ToolContext context) {
        return switch (name) {
            case "search_places" -> {
                List<Location> candidates = tools.searchPlaces(requiredText(arguments, "query"));
                context.latestCandidates = candidates;
                if (candidates.size() == 1) {
                    context.permittedLocationIds.add(candidates.getFirst().id());
                }
                yield candidates;
            }
            case "get_local_night_facts" -> {
                long locationId = requiredPositiveLong(arguments, "location_id");
                requirePermittedLocation(locationId, context);
                ObservationFactsResponse facts = tools.getLocalNightFacts(locationId);
                records.recordFacts(context.runId, facts);
                context.cloudFactsRequested = true;
                recordCloudForecast(facts, context);
                List<LocalDate> availableDates = facts == null || facts.outlook() == null
                        || facts.outlook().nights() == null
                        ? List.of()
                        : facts.outlook().nights().stream().map(NightOutlook::localDate)
                                .filter(java.util.Objects::nonNull).toList();
                context.supportedNightDates.put(locationId, availableDates);
                yield facts;
            }
            case "get_night_outlook" -> {
                long locationId = requiredPositiveLong(arguments, "location_id");
                requirePermittedLocation(locationId, context);
                LocalDate localDate = LocalDate.parse(requiredText(arguments, "local_date"));
                List<LocalDate> availableDates = context.supportedNightDates.get(locationId);
                if (availableDates == null) throw new LocalFactsRequiredException();
                if (!availableDates.contains(localDate)) {
                    throw new UnsupportedNightDateException(localDate, availableDates);
                }
                yield tools.getNightOutlook(locationId, localDate);
            }
            case "get_global_kp_forecast" -> tools.getGlobalKpForecast();
            case "get_three_day_storm_forecast" -> tools.getThreeDayStormForecast();
            case "get_active_geomagnetic_warnings" -> tools.getActiveGeomagneticWarnings();
            default -> throw new IllegalArgumentException("Unknown read-only tool.");
        };
    }

    private void requirePermittedLocation(long locationId, ToolContext context) {
        if (!context.permittedLocationIds.contains(locationId)) {
            throw new LocationSelectionRequiredException(context.latestCandidates);
        }
    }

    private void recordCloudForecast(ObservationFactsResponse facts, ToolContext context) {
        if (facts == null || facts.outlook() == null || facts.outlook().location() == null
                || facts.cloudForecast() == null || facts.cloudForecast().data() == null
                || facts.cloudForecast().data().cloudForecast() == null) return;
        ZoneId zone;
        try {
            zone = ZoneId.of(facts.outlook().location().timezone());
        } catch (RuntimeException invalidZone) {
            return;
        }
        context.latestCloudFacts = facts;
        for (WeatherCloudPoint point : facts.cloudForecast().data().cloudForecast()) {
            if (point == null || point.validAt() == null || point.cloudCoverPercent() == null) continue;
            String localHour = point.validAt().atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT));
            BigDecimal value = BigDecimal.valueOf(point.cloudCoverPercent()).stripTrailingZeros();
            context.cloudPercentagesByLocalHour.computeIfAbsent(localHour, ignored -> new TreeSet<>()).add(value);
        }
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

    private static final class ToolContext {
        private String runId;
        private int toolSequence;
        private final Set<Long> permittedLocationIds = new HashSet<>();
        private List<Location> latestCandidates = List.of();
        private final Map<Long, List<LocalDate>> supportedNightDates = new HashMap<>();
        private final Map<String, Set<BigDecimal>> cloudPercentagesByLocalHour = new HashMap<>();
        private boolean cloudFactsRequested;
        private ObservationFactsResponse latestCloudFacts;
    }

    private static final class LocationSelectionRequiredException extends RuntimeException {
        private final List<Location> candidates;

        private LocationSelectionRequiredException(List<Location> candidates) {
            this.candidates = candidates;
        }
    }

    private static final class LocalFactsRequiredException extends RuntimeException {}

    private ArrayNode buildToolDefinitions() {
        ArrayNode definitions = objectMapper.createArrayNode();
        definitions.add(tool("search_places", "Search matching places before selecting a location ID.",
                properties("query", "string", "City or place name"), List.of("query")));
        definitions.add(tool("get_local_night_facts", "Get source-backed local facts and supported localDate values for an application-selected location or a unique search result.",
                properties("location_id", "integer", "Selected provider location ID"), List.of("location_id")));

        ObjectNode nightProperties = objectMapper.createObjectNode();
        nightProperties.set("location_id", property("integer", "Selected provider location ID"));
        nightProperties.set("local_date", property("string", "Local calendar date in YYYY-MM-DD format"));
        definitions.add(tool("get_night_outlook", "Get one local night outlook using a permitted location ID and an exact localDate returned by get_local_night_facts.",
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
