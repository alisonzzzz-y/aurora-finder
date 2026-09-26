package com.aurora.observation.service;

import com.aurora.observation.dto.AssistantChatRequest;
import com.aurora.observation.dto.AssistantChatResponse;
import com.aurora.observation.dto.Location;
import com.aurora.observation.provider.OpenAiAssistantProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        verify(tools).getLocalNightFacts(2964574);
        verify(tools, never()).getLocalNightFacts(4192510);
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
}
