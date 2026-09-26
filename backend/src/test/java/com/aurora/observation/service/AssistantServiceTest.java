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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTest {
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
        ObjectNode message = mapper.createObjectNode();
        message.put("type", "message");
        ArrayNode content = mapper.createArrayNode();
        ObjectNode text = mapper.createObjectNode();
        text.put("type", "output_text");
        text.put("text", "Please choose the Dublin in Ireland.");
        content.add(text);
        message.set("content", content);
        secondOutput.add(message);
        secondResponse.set("output", secondOutput);

        when(openAi.respond(anyString(), any(ArrayNode.class), any(ArrayNode.class)))
                .thenReturn(firstResponse, secondResponse);
        when(openAi.model()).thenReturn("gpt-5-mini");
        when(tools.searchPlaces("Dublin")).thenReturn(List.of(
                new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
                        53.33306, -6.24889, "Europe/Dublin")));

        AssistantChatResponse response = service.chat(new AssistantChatRequest(
                "How is Dublin tonight?", "en", null, List.of()));

        assertEquals("Please choose the Dublin in Ireland.", response.answer());
        assertEquals("gpt-5-mini", response.model());
        verify(tools).searchPlaces("Dublin");
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
