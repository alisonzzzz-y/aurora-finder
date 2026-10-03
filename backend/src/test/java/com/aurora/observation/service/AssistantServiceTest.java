package com.aurora.observation.service;

import com.aurora.observation.dto.AssistantChatRequest;
import com.aurora.observation.dto.AssistantMessage;
import com.aurora.observation.dto.AssistantChatResponse;
import com.aurora.observation.dto.FactFetchStatus;
import com.aurora.observation.dto.FactTimeScope;
import com.aurora.observation.dto.ForecastCoverage;
import com.aurora.observation.dto.LocalAuroraActivityResponse;
import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.NightOutlook;
import com.aurora.observation.dto.ObservationFactsResponse;
import com.aurora.observation.dto.OutlookLevel;
import com.aurora.observation.dto.OutlookReasonCode;
import com.aurora.observation.dto.OutlookResponse;
import com.aurora.observation.dto.RuleStatus;
import com.aurora.observation.dto.SourceFact;
import com.aurora.observation.dto.WeatherForecastResponse;
import com.aurora.observation.dto.WeatherCloudPoint;
import com.aurora.observation.provider.OpenAiAssistantProvider;
import com.aurora.observation.provider.ProviderFailure;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.time.LocalDate;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTest {
    @Test
    void oversizedNumericCandidateReplyAsksAgainInsteadOfThrowing() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        Location ireland = new Location(1, "Dublin", "Leinster", "Dublin City", "Ireland", 53, -6, "Europe/Dublin");
        Location usa = new Location(2, "Dublin", "Ohio", "Franklin", "United States", 40, -83, "America/New_York");
        AssistantService service = new AssistantService(openAi, tools, new ObjectMapper());
        AssistantChatResponse response = service.chat(new AssistantChatRequest("9".repeat(1000), "en", null,
                List.of(new AssistantMessage("assistant", "Choose a place", List.of(ireland, usa)))));
        assertEquals(2, response.locationCandidates().size());
        verify(openAi, never()).respond(anyString(), any(), any());
    }

    @Test
    void replacesHourlyCloudValuesThatDoNotMatchTheSelectedLocationSource() {
        AssistantChatResponse response = chatWithCloudSource(
                "At 20:00 cloud cover is 72%, and at 21:00 it is 18.7%.");

        assertTrue(response.answer().contains("couldn't verify the hourly cloud values"));
        assertTrue(response.answer().contains("local cloud forecast chart"));
    }

    @Test
    void keepsHourlyCloudValuesThatMatchTheSelectedLocationSource() {
        AssistantChatResponse response = chatWithCloudSource(
                "At 20:00 cloud cover is 78.9%, and at 21:00 it is 18.7%.");

        assertEquals("At 20:00 cloud cover is 78.9%, and at 21:00 it is 18.7%.", response.answer());
    }

    @Test
    void rejectsHourlyCloudValuesWhenTheSourceHasNoCloudPoints() {
        AssistantChatResponse response = chatWithCloudSource(
                "At 20:00 cloud cover is 72%.", true);

        assertTrue(response.answer().contains("couldn't verify the hourly cloud values"));
    }

    @Test
    void rejectedSummaryStillShowsExactSourceSamplesWithLocalDates() {
        String answer = chatWithCloudSource("At 20:00 cloud cover is 72%.").answer();
        assertTrue(answer.contains("2026-09-28 20:00 +01:00: 78.9%"));
        assertTrue(answer.contains("2026-09-28 21:00 +01:00: 18.7%"));
        assertTrue(answer.contains("Source: MET Norway"));
        assertTrue(answer.contains("https://example.test/weather"));
        assertFalse(chatWithCloudSource("At 20:00 cloud cover is 72%.", true).answer().contains("Sample readings"));
    }

    private AssistantChatResponse chatWithCloudSource(String answer) {
        return chatWithCloudSource(answer, false);
    }

    @Test
    void matchesCloudReadingsInQuarterHourTimezonesWithoutRoundingTheHour() {
        String answer = "At 00:45 cloud cover is 78.9%, and at 01:45 it is 18.7%.";
        assertEquals(answer, chatWithCloudSource(answer, false, "Asia/Kathmandu").answer());
        assertTrue(chatWithCloudSource("At 00:00 cloud cover is 78.9%.", false, "Asia/Kathmandu")
                .answer().contains("couldn't verify"));
    }

    private AssistantChatResponse chatWithCloudSource(String answer, boolean emptyCloudForecast) {
        return chatWithCloudSource(answer, emptyCloudForecast, "Europe/Dublin");
    }

    private AssistantChatResponse chatWithCloudSource(String answer, boolean emptyCloudForecast, String timezone) {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        Location dublin = new Location(42, "Dublin", "Leinster", "Dublin City", "Ireland",
                53.33306, -6.24889, timezone);
        Instant retrievedAt = Instant.parse("2026-09-28T00:00:00Z");
        NightOutlook night = new NightOutlook(LocalDate.of(2026, 9, 28), "+01:00",
                retrievedAt, retrievedAt.plusSeconds(86400), OutlookLevel.INSUFFICIENT_DATA,
                OutlookReasonCode.RULES_NOT_VALIDATED, null);
        OutlookResponse outlook = new OutlookResponse(dublin, retrievedAt, RuleStatus.NOT_VALIDATED, List.of(night));
        WeatherForecastResponse forecast = new WeatherForecastResponse(retrievedAt, retrievedAt.plusSeconds(86400),
                "MET Norway", dublin.latitude(), dublin.longitude(), emptyCloudForecast ? List.of() : List.of(
                        new WeatherCloudPoint(Instant.parse("2026-09-28T19:00:00Z"), 78.9),
                        new WeatherCloudPoint(Instant.parse("2026-09-28T20:00:00Z"), 18.7)));
        SourceFact<WeatherForecastResponse> cloudFact = new SourceFact<>(FactFetchStatus.CURRENT,
                FactTimeScope.THREE_LOCAL_NIGHTS, retrievedAt, retrievedAt, retrievedAt,
                retrievedAt, retrievedAt.plusSeconds(86400), "MET Norway", "https://example.test/weather",
                null, forecast);
        ObservationFactsResponse facts = new ObservationFactsResponse(retrievedAt, outlook, null,
                cloudFact, null, null, FactFetchStatus.CURRENT);
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(responseWithFunctionCall(mapper, "get_local_night_facts", "{\"location_id\":42}"))
                .thenReturn(responseWithText(mapper, answer));
        when(openAi.model()).thenReturn("gpt-6-luna");
        when(tools.getLocalNightFacts(42)).thenReturn(facts);

        return new AssistantService(openAi, tools, mapper).chat(
                new AssistantChatRequest("What is the cloud cover tonight?", "en", 42L, List.of()));
    }

    @Test
    void tellsAssistantNotToInventLocalAuroraRecurrenceIntervals() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObjectMapper mapper = new ObjectMapper();
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(responseWithText(mapper,
                        "There is no validated local dataset from which to give a reliable interval."));
        when(openAi.model()).thenReturn("gpt-6-luna");
        org.mockito.ArgumentCaptor<String> instructions = org.mockito.ArgumentCaptor.forClass(String.class);

        AssistantChatResponse result = new AssistantService(openAi, mock(ObservationToolsService.class), mapper)
                .chat(new AssistantChatRequest(
                        "How often can people see aurora around Dublin or Cork?", "en", null, List.of()));

        assertTrue(result.answer().contains("no validated local dataset"));
        verify(openAi).respond(instructions.capture(), any(ArrayNode.class), any(ArrayNode.class));
        assertTrue(instructions.getValue().contains("cannot give a reliable interval"));
        assertTrue(instructions.getValue().contains("Never estimate that"));
        assertTrue(instructions.getValue().contains("frequency from latitude, Kp"));
        assertTrue(instructions.getValue().contains("use only the exact"));
        assertTrue(instructions.getValue().contains("Never guess a date or use the server's date"));
        assertTrue(instructions.getValue().contains("UTC offset"));
        assertTrue(instructions.getValue().contains("Mention unavailable or"));
        assertTrue(instructions.getValue().contains("conflicting percentages to"));
    }

    @Test
    void resolvesExactFullLocationLabelFromAmbiguousResults() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        AssistantService service = new AssistantService(openAi, tools, mapper);
        List<Location> candidates = List.of(
                new Location(2964574, "Dublin", "Leinster", "Dublin City", "Ireland", 53.33306, -6.24889, "Europe/Dublin"),
                new Location(1, "Dublin", "Georgia", "Laurens", "United States", 32.5, -82.9, "America/New_York"),
                new Location(2, "Dublin", "California", "Alameda", "United States", 37.7, -121.9, "America/Los_Angeles"),
                new Location(3, "Dublin", "Ohio", "Franklin", "United States", 40.1, -83.1, "America/New_York"),
                new Location(4, "Dublin", "Texas", "Erath", "United States", 32.1, -98.3, "America/Chicago"),
                new Location(5, "Dublin", "Virginia", "Pulaski", "United States", 37.1, -80.7, "America/New_York"),
                new Location(6, "Dublin", "Pennsylvania", "Bucks", "United States", 40.3, -75.1, "America/New_York"),
                new Location(7, "Dublin", "New Hampshire", "Cheshire County", "United States", 42.9, -72.1, "America/New_York"),
                new Location(8, "Resaca", "Georgia", "Gordon", "United States", 34.5, -84.9, "America/New_York"),
                new Location(9, "Dublin", "Indiana", "Wayne", "United States", 39.8, -84.9, "America/Indiana/Indianapolis"));
        ObjectNode factsResponse = mapper.createObjectNode();
        ArrayNode factsOutput = mapper.createArrayNode();
        factsOutput.add(functionCall(mapper, "get_local_night_facts", "{\"location_id\":2964574}"));
        factsResponse.set("output", factsOutput);
        ObjectNode answerResponse = mapper.createObjectNode();
        ArrayNode answerOutput = mapper.createArrayNode();
        ObjectNode answer = mapper.createObjectNode();
        answer.put("type", "message");
        ArrayNode content = mapper.createArrayNode();
        ObjectNode text = mapper.createObjectNode();
        text.put("type", "output_text");
        text.put("text", "Cloud data for Dublin, Ireland.");
        content.add(text);
        answer.set("content", content);
        answerOutput.add(answer);
        answerResponse.set("output", answerOutput);
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(factsResponse, answerResponse);
        when(openAi.model()).thenReturn("gpt-6-luna");

        AssistantChatResponse response = service.chat(new AssistantChatRequest(
                "Dublin, Leinster, Dublin City, Ireland", "en", null,
                List.of(new com.aurora.observation.dto.AssistantMessage("assistant", "Which Dublin?", candidates))));

        assertEquals("Cloud data for Dublin, Ireland.", response.answer());
        assertTrue(response.locationCandidates().isEmpty());
        verify(tools).getLocalNightFacts(2964574);
    }

    @Test
    void resolvesChineseCountryAndPlaceReplyFromPreviousCandidateList() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        AssistantService service = new AssistantService(openAi, tools, mapper);
        Location ireland = new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        Location georgia = new Location(4192510, "Dublin", "Georgia", "Laurens County", "United States",
                32.54044, -82.90375, "America/New_York");

        ObjectNode factsResponse = mapper.createObjectNode();
        ArrayNode factsOutput = mapper.createArrayNode();
        factsOutput.add(functionCall(mapper, "get_local_night_facts", "{\"location_id\":2964574}"));
        factsResponse.set("output", factsOutput);
        ObjectNode answerResponse = mapper.createObjectNode();
        ArrayNode answerOutput = mapper.createArrayNode();
        ObjectNode answer = mapper.createObjectNode();
        answer.put("type", "message");
        ArrayNode content = mapper.createArrayNode();
        ObjectNode text = mapper.createObjectNode();
        text.put("type", "output_text");
        text.put("text", "Here is the cloud forecast for Dublin, Ireland.");
        content.add(text);
        answer.set("content", content);
        answerOutput.add(answer);
        answerResponse.set("output", answerOutput);
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(factsResponse, answerResponse);
        when(openAi.model()).thenReturn("gpt-6-luna");
        when(tools.getLocalNightFacts(2964574)).thenReturn(null);

        AssistantChatResponse response = service.chat(new AssistantChatRequest("爱尔兰都柏林市", "zh", null, List.of(
                new com.aurora.observation.dto.AssistantMessage("user", "都柏林目前有哪些云量数据？"),
                new com.aurora.observation.dto.AssistantMessage("assistant", "Which Dublin?", List.of(ireland, georgia)))));

        assertEquals("Here is the cloud forecast for Dublin, Ireland.", response.answer());
        assertTrue(response.locationCandidates().isEmpty());
        verify(tools).getLocalNightFacts(2964574);
        verify(tools, never()).getLocalNightFacts(4192510);
        verify(openAi, times(2)).respond(anyString(), any(ArrayNode.class), any(ArrayNode.class));
    }

    @Test
    void resolvesDublinCityFromSeveralIrishSubareaCandidatesInOneReply() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(responseWithFunctionCall(mapper, "get_local_night_facts",
                        "{\"location_id\":2964574}"))
                .thenReturn(responseWithText(mapper, "Here are the cloud facts for Dublin City."));
        when(openAi.model()).thenReturn("gpt-6-luna");
        when(tools.getLocalNightFacts(2964574)).thenReturn(null);

        List<Location> candidates = List.of(
                new Location(2964574, "Dublin", "Leinster", "Dublin City", "Ireland",
                        53.33306, -6.24889, "Europe/Dublin"),
                new Location(7001, "Dublin South", "Leinster", "South Dublin", "Ireland",
                        53.29, -6.36, "Europe/Dublin"),
                new Location(7002, "Dublin Airport", "Leinster", "Fingal", "Ireland",
                        53.42, -6.27, "Europe/Dublin"),
                new Location(7003, "Dublin Pike", "Munster", "County Cork", "Ireland",
                        51.93, -8.53, "Europe/Dublin"));

        AssistantChatResponse response = new AssistantService(openAi, tools, mapper).chat(
                new AssistantChatRequest("都柏林市", "zh", null, List.of(
                        new com.aurora.observation.dto.AssistantMessage("user",
                                "都柏林目前有哪些云量数据？"),
                        new com.aurora.observation.dto.AssistantMessage("assistant",
                                "请选择都柏林的具体地点。", candidates))));

        assertEquals("Here are the cloud facts for Dublin City.", response.answer());
        assertTrue(response.locationCandidates().isEmpty());
        verify(tools).getLocalNightFacts(2964574);
        verify(tools, never()).getLocalNightFacts(7001);
        verify(tools, never()).getLocalNightFacts(7002);
        verify(tools, never()).getLocalNightFacts(7003);
        verify(openAi, times(2)).respond(anyString(), any(ArrayNode.class), any(ArrayNode.class));
    }

    @Test
    void keepsAskingWithNumberedChoicesInsteadOfCallingModelWhenReplyIsUnclear() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObjectMapper mapper = new ObjectMapper();
        when(openAi.model()).thenReturn("gpt-6-luna");
        AssistantService service = new AssistantService(openAi, mock(ObservationToolsService.class), mapper);
        List<Location> candidates = List.of(
                new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland", 53.33306, -6.24889, "Europe/Dublin"),
                new Location(4192510, "Dublin", "Georgia", "Laurens County", "United States", 32.54044, -82.90375, "America/New_York"));

        AssistantChatResponse response = service.chat(new AssistantChatRequest("确认", "zh", null,
                List.of(new com.aurora.observation.dto.AssistantMessage("assistant", "Which Dublin?", candidates))));

        assertTrue(response.answer().contains("1."));
        assertTrue(response.answer().contains("2."));
        assertEquals(2, response.locationCandidates().size());
        verify(openAi, never()).respond(anyString(), any(ArrayNode.class), any(ArrayNode.class));
    }

    @Test
    void doesNotReuseCandidatesFromBeforeTheMostRecentUserReply() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode response = mapper.createObjectNode();
        ArrayNode output = mapper.createArrayNode();
        ObjectNode answer = mapper.createObjectNode();
        answer.put("type", "message");
        ArrayNode content = mapper.createArrayNode();
        ObjectNode text = mapper.createObjectNode();
        text.put("type", "output_text");
        text.put("text", "Hello.");
        content.add(text);
        answer.set("content", content);
        output.add(answer);
        response.set("output", output);
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class))).thenReturn(response);
        when(openAi.model()).thenReturn("gpt-6-luna");

        List<Location> oldCandidates = List.of(
                new Location(2964574, "Dublin", "Leinster", "Dublin City", "Ireland",
                        53.33306, -6.24889, "Europe/Dublin"),
                new Location(4192510, "Dublin", "Georgia", "Laurens", "United States",
                        32.54044, -82.90375, "America/New_York"));
        AssistantChatResponse result = new AssistantService(openAi, mock(ObservationToolsService.class), mapper)
                .chat(new AssistantChatRequest("Hello", "en", null, List.of(
                        new com.aurora.observation.dto.AssistantMessage("assistant", "Which Dublin?", oldCandidates),
                        new com.aurora.observation.dto.AssistantMessage("user", "Ireland", null),
                        new com.aurora.observation.dto.AssistantMessage("assistant", "Here are the facts.", null))));

        assertEquals("Hello.", result.answer());
        assertTrue(result.locationCandidates().isEmpty());
    }

    @Test
    void executesReadOnlyToolCallBeforeReturningAnswer() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        AssistantService service = new AssistantService(openAi, tools, mapper);

        ObjectNode firstResponse = mapper.createObjectNode();
        ArrayNode firstOutput = mapper.createArrayNode();
        ObjectNode call = mapper.createObjectNode();
        call.put("type", "function_call");
        call.put("call_id", "call-1");
        call.put("name", "search_places");
        call.put("arguments", "{\"query\":\"Dublin\"}");
        firstOutput.add(call);
        firstResponse.set("output", firstOutput);

        ObjectNode secondResponse = mapper.createObjectNode();
        ArrayNode secondOutput = mapper.createArrayNode();
        secondOutput.add(functionCall(mapper, "get_local_night_facts", "{\"location_id\":2964574}"));
        secondResponse.set("output", secondOutput);

        ObjectNode thirdResponse = mapper.createObjectNode();
        ArrayNode thirdOutput = mapper.createArrayNode();
        ObjectNode message = mapper.createObjectNode();
        message.put("type", "message");
        ArrayNode content = mapper.createArrayNode();
        ObjectNode text = mapper.createObjectNode();
        text.put("type", "output_text");
        text.put("text", "I found the unique Dublin match in Ireland.");
        content.add(text);
        message.set("content", content);
        thirdOutput.add(message);
        thirdResponse.set("output", thirdOutput);

        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(firstResponse, secondResponse, thirdResponse);
        when(openAi.model()).thenReturn("gpt-6-luna");
        when(tools.searchPlaces("Dublin")).thenReturn(List.of(
                new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
                        53.33306, -6.24889, "Europe/Dublin")));

        AssistantChatResponse response = service.chat(new AssistantChatRequest(
                "How is Dublin tonight?", "en", null, List.of()));

        assertEquals("I found the unique Dublin match in Ireland.", response.answer());
        assertEquals("gpt-6-luna", response.model());
        verify(tools).searchPlaces("Dublin");
        verify(tools).getLocalNightFacts(2964574);
    }

    @Test
    void replacesSelfCorrectedConflictingCloudValuesWithSafeGuidance() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(responseWithFunctionCall(mapper, "get_local_night_facts",
                                "{\"location_id\":2964574}"))
                .thenReturn(responseWithText(mapper,
                        "10:00 云量为 21.1%。更正：10:00 云量为 25.8%。"));
        when(openAi.model()).thenReturn("gpt-6-luna");

        AssistantChatResponse result = new AssistantService(openAi, tools, mapper).chat(
                new AssistantChatRequest("Dublin 今晚云量如何？", "zh", 2964574L, List.of()));

        assertEquals("这次回复中的逐小时云量数值前后不一致，我不想给你错误数据。请查看页面里的当地云量预报图，或稍后重试。",
                result.answer());
        verify(tools).getLocalNightFacts(2964574);
    }

    @Test
    void keepsCloudAnswerWhenCorrectionRepeatsTheSameHourlyValue() {
        String answer = "20:00 云量为 78.9%。更正：20:00 云量为 78.9%。";
        AssistantChatResponse result = chatWithCloudSource(answer);

        assertEquals(answer, result.answer());
    }

    @Test
    void refusesLocalFactsUntilAmbiguousPlaceIsResolved() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        AssistantService service = new AssistantService(openAi, tools, mapper);

        ObjectNode searchResponse = mapper.createObjectNode();
        ArrayNode searchOutput = mapper.createArrayNode();
        searchOutput.add(functionCall(mapper, "search_places", "{\"query\":\"Dublin\"}"));
        searchResponse.set("output", searchOutput);

        ObjectNode factsResponse = mapper.createObjectNode();
        ArrayNode factsOutput = mapper.createArrayNode();
        factsOutput.add(functionCall(mapper, "get_local_night_facts", "{\"location_id\":2964574}"));
        factsResponse.set("output", factsOutput);

        ObjectNode answerResponse = mapper.createObjectNode();
        ArrayNode answerOutput = mapper.createArrayNode();
        ObjectNode answer = mapper.createObjectNode();
        answer.put("type", "message");
        ArrayNode content = mapper.createArrayNode();
        ObjectNode text = mapper.createObjectNode();
        text.put("type", "output_text");
        text.put("text", "Which Dublin do you mean, Ireland or Georgia?");
        content.add(text);
        answer.set("content", content);
        answerOutput.add(answer);
        answerResponse.set("output", answerOutput);

        org.mockito.ArgumentCaptor<ArrayNode> requestInputs = org.mockito.ArgumentCaptor.forClass(ArrayNode.class);
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(searchResponse, factsResponse, answerResponse);
        when(openAi.model()).thenReturn("gpt-6-luna");
        when(tools.searchPlaces("Dublin")).thenReturn(List.of(
                new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
                        53.33306, -6.24889, "Europe/Dublin"),
                new Location(4192510, "Dublin", "Georgia", "Laurens County", "United States",
                        32.54044, -82.90375, "America/New_York")));

        AssistantChatResponse response = service.chat(new AssistantChatRequest(
                "How is Dublin tonight?", "en", null, List.of()));

        assertEquals("Which Dublin do you mean, Ireland or Georgia?", response.answer());
        assertEquals(2, response.locationCandidates().size());
        verify(tools).searchPlaces("Dublin");
        verify(tools, never()).getLocalNightFacts(2964574);
        verify(openAi, times(3)).respond(anyString(), requestInputs.capture(), any(ArrayNode.class));
        String guardedInput = requestInputs.getAllValues().get(2).toString();
        assertTrue(guardedInput.contains("location_selection_required"));
        assertTrue(guardedInput.contains("Ireland"));
        assertTrue(guardedInput.contains("Georgia"));
    }

    private ObjectNode functionCall(ObjectMapper mapper, String name, String arguments) {
        ObjectNode call = mapper.createObjectNode();
        call.put("type", "function_call");
        call.put("call_id", "call-" + name);
        call.put("name", name);
        call.put("arguments", arguments);
        return call;
    }

    @Test
    void rejectsResponseWithoutOutputItems() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObjectMapper mapper = new ObjectMapper();
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(mapper.createObjectNode());
        AssistantService service = new AssistantService(openAi, mock(ObservationToolsService.class), mapper);

        AssistantUnavailableException error = org.junit.jupiter.api.Assertions.assertThrows(
                AssistantUnavailableException.class,
                () -> service.chat(new AssistantChatRequest("Hello", "en", null, List.of())));

        assertTrue(error.getMessage().contains("invalid response"));
    }

    @Test
    void stopsAfterFiveModelToolRoundsToBoundAssistantCost() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode response = mapper.createObjectNode();
        ArrayNode output = mapper.createArrayNode();
        output.add(functionCall(mapper, "get_global_kp_forecast", "{}"));
        response.set("output", output);
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(response);

        AssistantUnavailableException error = org.junit.jupiter.api.Assertions.assertThrows(
                AssistantUnavailableException.class,
                () -> new AssistantService(openAi, tools, mapper)
                        .chat(new AssistantChatRequest("What is the current global activity?", "en", null, List.of())));

        assertTrue(error.getMessage().contains("too many tool steps"));
        verify(openAi, times(5)).respond(anyString(), any(ArrayNode.class), any(ArrayNode.class));
        verify(tools, times(5)).getGlobalKpForecast();
    }

    @Test
    void givesModelSafeUnavailableStatusWhenLocalFactsToolFails() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(responseWithFunctionCall(mapper, "get_local_night_facts", "{\"location_id\":42}"))
                .thenReturn(responseWithText(mapper, "Cloud data is currently unavailable."));
        when(openAi.model()).thenReturn("gpt-6-luna");
        when(tools.getLocalNightFacts(42)).thenThrow(new IllegalStateException("secret upstream URL and token"));
        org.mockito.ArgumentCaptor<ArrayNode> requestInputs = org.mockito.ArgumentCaptor.forClass(ArrayNode.class);

        AssistantChatResponse result = new AssistantService(openAi, tools, mapper).chat(
                new AssistantChatRequest("What are the clouds like?", "en", 42L, List.of()));

        assertEquals("Cloud data is currently unavailable.", result.answer());
        verify(openAi, times(2)).respond(anyString(), requestInputs.capture(), any(ArrayNode.class));
        String secondRequest = requestInputs.getAllValues().get(1).toString();
        assertTrue(secondRequest.contains("unavailable"));
        assertTrue(secondRequest.contains("currently unavailable"));
        assertFalse(secondRequest.contains("secret upstream URL"));
    }

    @Test
    void rejectsModelSuppliedLocationIdThatDiffersFromApplicationSelection() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        org.mockito.ArgumentCaptor<ArrayNode> requestInputs = org.mockito.ArgumentCaptor.forClass(ArrayNode.class);
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(responseWithFunctionCall(mapper, "get_local_night_facts", "{\"location_id\":99}"))
                .thenReturn(responseWithText(mapper, "Please use the selected place."));
        when(openAi.model()).thenReturn("gpt-6-luna");

        AssistantChatResponse result = new AssistantService(openAi, tools, mapper).chat(
                new AssistantChatRequest("What are the local conditions?", "en", 42L, List.of()));

        assertEquals("Please use the selected place.", result.answer());
        verify(tools, never()).getLocalNightFacts(99);
        verify(tools, never()).getLocalNightFacts(42);
        verify(openAi, times(2)).respond(anyString(), requestInputs.capture(), any(ArrayNode.class));
        String followUpInput = requestInputs.getAllValues().get(1).toString();
        assertTrue(followUpInput.contains("location_selection_required"));
        assertFalse(followUpInput.contains("\"location_id\":99"));
    }

    @Test
    void passesLocalTimeAndSourceFreshnessFieldsToModelWithoutRecomputingThem() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        Instant retrievedAt = Instant.parse("2026-09-28T00:15:00Z");
        LocalDate localDate = LocalDate.of(2026, 9, 28);
        Location dublin = new Location(42, "Dublin", "Leinster", "Dublin City", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        NightOutlook night = new NightOutlook(localDate, "+01:00", retrievedAt,
                retrievedAt.plusSeconds(86400), OutlookLevel.INSUFFICIENT_DATA,
                OutlookReasonCode.RULES_NOT_VALIDATED, null);
        OutlookResponse outlook = new OutlookResponse(dublin, retrievedAt, RuleStatus.NOT_VALIDATED,
                List.of(night));
        SourceFact<LocalAuroraActivityResponse> aurora = new SourceFact<>(FactFetchStatus.EXPIRED,
                FactTimeScope.SHORT_RANGE, retrievedAt, retrievedAt.minusSeconds(7200), retrievedAt,
                retrievedAt.minusSeconds(3600), retrievedAt.plusSeconds(3600), "NOAA", "https://example.test/aurora",
                ProviderFailure.UPSTREAM_ERROR, null);
        SourceFact<WeatherForecastResponse> clouds = new SourceFact<>(FactFetchStatus.PARTIAL,
                FactTimeScope.THREE_LOCAL_NIGHTS, retrievedAt, null, null, retrievedAt,
                retrievedAt.plusSeconds(86400), "MET Norway", "https://example.test/weather", null, null);
        ObservationFactsResponse localFacts = new ObservationFactsResponse(retrievedAt, outlook, aurora,
                clouds, null, new ForecastCoverage(ForecastCoverage.Status.NO_OVERLAP,
                FactTimeScope.SHORT_RANGE, FactTimeScope.THREE_LOCAL_NIGHTS, null, null, 0),
                FactFetchStatus.PARTIAL);
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(responseWithFunctionCall(mapper, "get_local_night_facts", "{\"location_id\":42}"))
                .thenReturn(responseWithText(mapper, "The local outlook is incomplete and one source is expired."));
        when(openAi.model()).thenReturn("gpt-6-luna");
        when(tools.getLocalNightFacts(42)).thenReturn(localFacts);
        org.mockito.ArgumentCaptor<ArrayNode> requestInputs = org.mockito.ArgumentCaptor.forClass(ArrayNode.class);

        AssistantChatResponse response = new AssistantService(openAi, tools, mapper).chat(
                new AssistantChatRequest("What is the outlook tonight?", "en", 42L, List.of()));

        assertTrue(response.answer().contains("incomplete"));
        verify(openAi, times(2)).respond(anyString(), requestInputs.capture(), any(ArrayNode.class));
        String factsSentToModel = requestInputs.getAllValues().get(1).toString();
        assertTrue(factsSentToModel.contains("Europe/Dublin"));
        assertTrue(factsSentToModel.contains("2026-09-28"));
        assertTrue(factsSentToModel.contains("+01:00"));
        assertTrue(factsSentToModel.contains("EXPIRED"));
        assertTrue(factsSentToModel.contains("PARTIAL"));
        assertTrue(factsSentToModel.contains("NO_OVERLAP"));
        assertTrue(factsSentToModel.contains("NOAA"));
        assertTrue(factsSentToModel.contains("MET Norway"));
    }

    @Test
    void returnsSupportedLocalDatesToModelWhenRequestedNightIsOutOfRange() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        LocalDate requested = LocalDate.of(2026, 9, 27);
        List<LocalDate> available = List.of(requested.plusDays(1), requested.plusDays(2));
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(responseWithFunctionCall(mapper, "get_local_night_facts", "{\"location_id\":42}"))
                .thenReturn(responseWithFunctionCall(mapper, "get_night_outlook",
                        "{\"location_id\":42,\"local_date\":\"2026-09-27\"}"))
                .thenReturn(responseWithText(mapper, "That date is outside the available local forecast."));
        when(openAi.model()).thenReturn("gpt-6-luna");
        when(tools.getLocalNightFacts(42)).thenReturn(factsForDates(available));
        org.mockito.ArgumentCaptor<ArrayNode> requestInputs = org.mockito.ArgumentCaptor.forClass(ArrayNode.class);

        AssistantChatResponse result = new AssistantService(openAi, tools, mapper).chat(
                new AssistantChatRequest("What about tonight?", "en", 42L, List.of()));

        assertEquals("That date is outside the available local forecast.", result.answer());
        verify(openAi, times(3)).respond(anyString(), requestInputs.capture(), any(ArrayNode.class));
        String secondRequest = requestInputs.getAllValues().get(1).toString();
        String thirdRequest = requestInputs.getAllValues().get(2).toString();
        assertTrue(secondRequest.contains("2026-09-28"));
        assertTrue(secondRequest.contains("2026-09-29"));
        assertTrue(thirdRequest.contains("2026-09-28"));
        assertTrue(thirdRequest.contains("2026-09-29"));
        verify(tools, never()).getNightOutlook(42, requested);
    }

    @Test
    void requiresLocalFactsBeforeAcceptingAModelSuppliedNightDate() {
        OpenAiAssistantProvider openAi = mock(OpenAiAssistantProvider.class);
        ObservationToolsService tools = mock(ObservationToolsService.class);
        ObjectMapper mapper = new ObjectMapper();
        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(responseWithFunctionCall(mapper, "get_night_outlook",
                        "{\"location_id\":42,\"local_date\":\"2026-09-28\"}"))
                .thenReturn(responseWithText(mapper, "I need to check the supported local dates first."));
        when(openAi.model()).thenReturn("gpt-6-luna");
        org.mockito.ArgumentCaptor<ArrayNode> requestInputs = org.mockito.ArgumentCaptor.forClass(ArrayNode.class);

        AssistantChatResponse result = new AssistantService(openAi, tools, mapper).chat(
                new AssistantChatRequest("What about that night?", "en", 42L, List.of()));

        assertTrue(result.answer().contains("supported local dates"));
        verify(tools, never()).getNightOutlook(42, LocalDate.of(2026, 9, 28));
        verify(openAi, times(2)).respond(anyString(), requestInputs.capture(), any(ArrayNode.class));
        assertTrue(requestInputs.getAllValues().get(1).toString().contains("local_facts_required"));
    }

    private ObservationFactsResponse factsForDates(List<LocalDate> dates) {
        Location location = new Location(42, "Dublin", "Leinster", "Dublin City", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        List<NightOutlook> nights = dates.stream().map(date -> new NightOutlook(date, "+01:00",
                Instant.EPOCH, Instant.EPOCH, OutlookLevel.INSUFFICIENT_DATA,
                OutlookReasonCode.RULES_NOT_VALIDATED, null)).toList();
        OutlookResponse outlook = new OutlookResponse(location, Instant.EPOCH, RuleStatus.NOT_VALIDATED, nights);
        return new ObservationFactsResponse(Instant.EPOCH, outlook, null, null, null, null, null);
    }

    private ObjectNode responseWithFunctionCall(ObjectMapper mapper, String name, String arguments) {
        ObjectNode response = mapper.createObjectNode();
        ArrayNode output = mapper.createArrayNode();
        output.add(functionCall(mapper, name, arguments));
        response.set("output", output);
        return response;
    }

    private ObjectNode responseWithText(ObjectMapper mapper, String textValue) {
        ObjectNode response = mapper.createObjectNode();
        ArrayNode output = mapper.createArrayNode();
        ObjectNode message = mapper.createObjectNode();
        message.put("type", "message");
        ArrayNode content = mapper.createArrayNode();
        ObjectNode text = mapper.createObjectNode();
        text.put("type", "output_text");
        text.put("text", textValue);
        content.add(text);
        message.set("content", content);
        output.add(message);
        response.set("output", output);
        return response;
    }
}
