package com.aurora.observation.service;

import com.aurora.observation.dto.AssistantChatRequest;
import com.aurora.observation.dto.AssistantChatResponse;
import com.aurora.observation.dto.AssistantMessage;
import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.NightOutlook;
import com.aurora.observation.dto.ObservationFactsResponse;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AssistantService {
    private static final int MAX_MODEL_TURNS = 5;
    private static final Pattern HOURLY_CLOUD_PERCENT = Pattern.compile(
            "(?m)(?<!\\d)((?:[01]?\\d|2[0-3]))(?::([0-5]\\d)|[时点])[^\\n%％]{0,60}?([0-9]+(?:\\.[0-9]+)?)\\s*[%％]");
    private static final Pattern CORRECTION_WORD = Pattern.compile(
            "(?i)\\b(?:correction|corrected|actually|i mean|to correct)\\b|更正|修正|改为|应为|更准确地说");
    private static final String INSTRUCTIONS = """
            You are the read-only assistant inside Aurora Finder. Answer in the user's requested language.
            Use the provided tools for current aurora, geomagnetic, cloud, darkness, location, and source facts.
            Do not rely on remembered forecast data. Never invent observations, dates, source status, or a person's
            probability of seeing aurora. The OVATION value is a model signal, not a calibrated viewing probability.
            For questions about a recent past event, always call get_recent_geomagnetic_history before answering.
            It includes queriedAtUtc, the actual reference time. Interpret yesterday or previous nights in the
            requested place's timezone, never relative to an old forecast timestamp. State the dates you used.
            Use observed/estimated past Kp readings to explain global activity even when local history is absent.
            Do not claim that no historical data exists without checking. Distinguish global readings, saved local
            forecasts and confirmed local sightings. The app has no verified live sighting feed or web search tool.
            A missing snapshot is not evidence that aurora was absent. A user's report can be acknowledged as
            user-provided, not independently verified. Do not substitute current conditions for a past night.
            After resolving the place, call get_local_history for the requested local date; it does not require
            get_local_night_facts or a date from the current forecast window. Historical dates are allowed.
            Explain basic aurora science when helpful, but use tools for event-specific claims and cite their source.
            For questions about how often aurora has historically been visible in a city or region, state that this
            app has no validated local recurrence dataset and cannot give a reliable interval. Never estimate that
            frequency from latitude, Kp, current OVATION values, or isolated community reports. Offer to check a
            supported current or near-term forecast instead.
            The viewingConditions level describes sky darkness and forecast clouds only, not aurora visibility or a probability.
            Keep local model activity separate. A low or zero nearest grid value does not rule out distant aurora or determine a whole-night opportunity.
            horizonAurora describes experimental poleward model candidates, not confirmed sightings or an official viewline.
            Mention its direction, distance and forecast time if available; NO_CANDIDATE does not mean aurora cannot be seen.
            Horizon clouds, terrain, brightness, emission height and light pollution have not been checked.
            If reporting its HIGH/MEDIUM/LOW level, give evaluatedAtUtc and state the rule is uncalibrated.
            Never turn a rating or model signal into a viewing percentage or extend it to a whole night.
            The tool's assistantCloudWindow is the only source for hourly cloud answers. It contains a single
            place, requested local date, exact start/end instants and localSamples with dates and UTC offsets.
            Tonight means the current calendar date at that place, even after midnight. The grouping window is
            local noon to next noon, not a guarantee of darkness. The following morning belongs to that night.
            Never use an earlier morning, another night or another place to fill a missing requested window.
            For an explicit date not already selected, call get_night_cloud_forecast after get_local_night_facts.
            DATE_SELECTION_REQUIRED means select each requested date with that tool, or clarify an ambiguous date.
            OUTSIDE_FORECAST_WINDOW, UNAVAILABLE or NO_DATA means no cloud samples for that request. Say so.
            When a user chooses a place after a clarification, answer their original question and keep its time
            scope. A follow-up such as tomorrow night changes the date, while keeping the resolved place.
            Missing future aurora data stays missing. Keep answers short, with at most three exact dated samples.
            Prefer plain text to Markdown tables. Do not repeat general warnings or ask for an already resolved place.
            Explain uncertainty plainly. Distinguish global geomagnetic activity from local viewing conditions.
            Search for a place before using local tools unless the application supplied a selected location ID.
            Use only an application-selected location ID, a unique candidate returned by search_places, or a
            location explicitly selected by the application from the previous turn's candidate list. If multiple
            candidates match, show them and ask the user to choose; do not query any candidate until the user has
            clarified. For current/future outlook dates only, call get_local_night_facts first and use only the exact
            localDate values returned for that place. Map tonight/tomorrow to a returned date only when unambiguous;
            otherwise ask the user to choose a date. Never guess a date or use the server's date. Include the place's
            UTC offset when tool data provides it. Keep answers concise and practical. Mention unavailable or
            incomplete source data instead of filling gaps.
            For cloud cover, use the returned percentages and local hours. Write samples as HH:mm: X%,
            preserving the source decimal values and local date. Use at most three samples from the requested night. When summarizing an interval with a
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
            return new AssistantChatResponse(answer.answer(), answer.model(), answer.locationCandidates(), runId, answer.selectedLocationId());
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
        toolContext.requestTime = AssistantCloudEvidence.requestTime(request.message(), history);
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

        boolean retriedIncomplete = false;
        for (int turn = 0; turn < MAX_MODEL_TURNS; turn++) {
            JsonNode response = openAi.respond(INSTRUCTIONS, input, toolDefinitions);
            if ("incomplete".equals(response.path("status").asText())) {
                if (!retriedIncomplete && "max_output_tokens".equals(response.path("incomplete_details").path("reason").asText())) {
                    retriedIncomplete = true;
                    input.add(message("user", "Give a complete, concise answer using the evidence already returned. Use at most 180 Chinese characters or 120 English words. Do not repeat a summary."));
                    continue;
                }
                throw new AssistantUnavailableException("The AI answer was incomplete. Please retry.");
            }
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
                Long selectedLocationId = choices.isEmpty() && toolContext.localFacts.size() == 1
                        ? toolContext.localFacts.keySet().iterator().next()
                        : choices.isEmpty() && toolContext.permittedLocationIds.size() == 1
                            ? toolContext.permittedLocationIds.iterator().next() : null;
                return new AssistantChatResponse(answer, openAi.model(), choices, null, selectedLocationId);
            }
        }
        throw new AssistantUnavailableException("The AI assistant used too many tool steps.");
    }

    private boolean hasConflictingCorrectedCloudValues(String answer) {
        if (!CORRECTION_WORD.matcher(answer).find()) return false;

        Map<String, String> percentagesByHour = new HashMap<>();
        Matcher matcher = HOURLY_CLOUD_PERCENT.matcher(answer);
        while (matcher.find()) {
            String hour = String.format(Locale.ROOT, "%02d:%s", Integer.parseInt(matcher.group(1)), matcher.group(2) == null ? "00" : matcher.group(2));
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
        if (!context.cloudFactsRequested) return false;
        Matcher matcher = HOURLY_CLOUD_PERCENT.matcher(answer);
        while (matcher.find()) {
            String hour = String.format(Locale.ROOT, "%02d:%s", Integer.parseInt(matcher.group(1)), matcher.group(2) == null ? "00" : matcher.group(2));
            BigDecimal stated = new BigDecimal(matcher.group(3));
            // Multiple places or nights need explicit attribution. Use the structured source summary instead
            // of accepting a value merely because that hour occurs somewhere in the combined evidence.
            if (context.cloudWindows.size() != 1) return true;
            var window = context.cloudWindows.values().iterator().next();
            ZoneId zone = ZoneId.of(window.location().timezone());
            var dateMatcher = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(answer.substring(0, matcher.start()));
            String date = null;
            while (dateMatcher.find()) date = dateMatcher.group(1);
            final String explicitDate = date;
            boolean matchesSource = window.points().stream().anyMatch(point -> {
                var local = point.validAt().atZone(zone);
                return local.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)).equals(hour)
                        && (explicitDate == null || explicitDate.equals(local.toLocalDate().toString()))
                        && BigDecimal.valueOf(point.cloudCoverPercent()).subtract(stated).abs().compareTo(roundingTolerance(stated)) <= 0;
            });
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
        StringBuilder summary = new StringBuilder();
        for (var window : context.cloudWindows.values()) {
            var place = window.location();
            summary.append("\n\n").append(place.name()).append(", ").append(place.country());
            if (window.localDate() != null) summary.append(" · ").append(window.localDate());
            summary.append(" · ").append(place.timezone()).append("\n");
            if (window.points().isEmpty()) {
                summary.append(chinese ? "所请求时段没有可用的云量预报。" : "No cloud forecast is available for the requested window.");
                continue;
            }
            summary.append(chinese ? "所请求时段的云量样本（当地时间）：\n" : "Sample readings for the requested window, in local time:\n");
            var formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm XXX", Locale.ROOT);
            // Spread samples across the available window instead of showing only its first two hours.
            var points = window.points();
            for (int index : java.util.stream.IntStream.of(0, points.size() / 2, points.size() - 1).distinct().toArray()) {
                var point = points.get(index);
                summary.append("- ").append(formatter.format(point.validAt().atZone(ZoneId.of(place.timezone())))).append(": ")
                        .append(BigDecimal.valueOf(point.cloudCoverPercent()).stripTrailingZeros().toPlainString()).append("%\n");
            }
            summary.append(chinese ? "数据来源：" : "Source: ").append(window.source());
            if (window.sourceUrl() != null && !window.sourceUrl().equals(window.source())) summary.append("\n").append(window.sourceUrl());
        }
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
                if (facts != null) context.localFacts.put(locationId, facts);
                List<LocalDate> availableDates = facts == null || facts.outlook() == null
                        || facts.outlook().nights() == null
                        ? List.of()
                        : facts.outlook().nights().stream().map(NightOutlook::localDate)
                                .filter(java.util.Objects::nonNull).toList();
                context.supportedNightDates.put(locationId, availableDates);
                yield scopedFacts(facts, context, null);
            }
            case "get_night_cloud_forecast" -> {
                long locationId = requiredPositiveLong(arguments, "location_id");
                requirePermittedLocation(locationId, context);
                ObservationFactsResponse facts = context.localFacts.get(locationId);
                if (facts == null) throw new LocalFactsRequiredException();
                yield scopedFacts(facts, context, LocalDate.parse(requiredText(arguments, "local_date")));
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
            case "get_recent_geomagnetic_history" -> tools.getRecentGeomagneticHistory();
            case "get_local_history" -> {
                long locationId = requiredPositiveLong(arguments, "location_id");
                requirePermittedLocation(locationId, context);
                yield tools.getLocalHistory(locationId, LocalDate.parse(requiredText(arguments, "local_date")));
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

    private Object scopedFacts(ObservationFactsResponse facts, ToolContext context, LocalDate date) {
        if (facts == null || facts.outlook() == null || facts.outlook().location() == null) return facts;
        var window = AssistantCloudEvidence.select(facts, context.requestTime, date);
        // Replace a broad first response for this place when a precise date is selected.
        context.cloudWindows.entrySet().removeIf(entry -> entry.getValue().location().id() == window.location().id()
                && entry.getValue().localDate() == null);
        context.cloudWindows.put(window.location().id() + ":" + window.localDate(), window);
        ObjectNode result = objectMapper.valueToTree(facts);
        ObjectNode selected = objectMapper.valueToTree(window);
        selected.remove("points");
        selected.set("localSamples", objectMapper.valueToTree(AssistantCloudEvidence.localSamples(window)));
        selected.put("windowMeaning", "Local noon to next noon for a requested night, or the next three hours for current conditions. Only future source samples are included; gaps are unknown.");
        result.set("assistantCloudWindow", selected);
        // The page still receives all three nights. The assistant only sees the selected weather evidence.
        if (result.path("cloudForecast").path("data") instanceof ObjectNode cloudData) {
            cloudData.set("cloudForecast", objectMapper.valueToTree(window.points()));
        }
        return result;
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
        private String requestTime;
        private final Map<Long, ObservationFactsResponse> localFacts = new HashMap<>();
        private final Map<String, AssistantCloudEvidence.Window> cloudWindows = new java.util.LinkedHashMap<>();
        private boolean cloudFactsRequested;
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
        definitions.add(tool("get_night_cloud_forecast", "Select cloud samples for one exact local night after get_local_night_facts. Returns dated local samples only within that window; unavailable windows contain no substitute values.",
                nightProperties, List.of("location_id", "local_date")));
        definitions.add(tool("get_night_outlook", "Get one local night outlook using a permitted location ID and an exact localDate returned by get_local_night_facts.",
                nightProperties, List.of("location_id", "local_date")));
        definitions.add(tool("get_recent_geomagnetic_history", "Read available completed observed/estimated global Kp intervals in the past 72 hours and the current reference time. Use for past-event questions before claiming history is unavailable. Not proof of local sightings.",
                objectMapper.createObjectNode(), List.of()));
        definitions.add(tool("get_local_history", "Read saved local forecast snapshots for a permitted location and historical local date (noon to next noon). Empty means no stored evidence, not no aurora. No current-night restriction.",
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
