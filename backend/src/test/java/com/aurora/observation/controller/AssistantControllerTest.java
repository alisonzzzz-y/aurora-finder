package com.aurora.observation.controller;

import com.aurora.observation.service.AssistantRateLimitException;
import com.aurora.observation.service.AssistantRequestLimiter;
import com.aurora.observation.service.AssistantService;
import com.aurora.observation.service.AssistantUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantControllerTest {
    private final AssistantService assistant = mock(AssistantService.class);
    private final AssistantRequestLimiter limiter = mock(AssistantRequestLimiter.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AssistantController(assistant, limiter))
                .setControllerAdvice(new ApiErrorHandler())
                .build();
    }

    @Test
    void acceptsLongAssistantHistorySoTheNextTurnCanContinue() throws Exception {
        String historyAnswer = "a".repeat(4000);
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"What about tomorrow?\",\"history\":[{\"role\":\"assistant\",\"content\":\""
                                + historyAnswer + "\"}]}"))
                .andExpect(status().isOk());
        verify(assistant).chat(any());
    }

    @Test
    void rejectsNullHistoryAndCandidateEntriesBeforeCallingAi() throws Exception {
        for (String history : new String[]{"[null]",
                "[{\"role\":\"assistant\",\"content\":\"Choose a place\",\"locationCandidates\":[null]}]"}) {
            mockMvc.perform(post("/api/v1/assistant/chat")
                            .contentType(APPLICATION_JSON)
                            .content("{\"message\":\"1\",\"history\":" + history + "}"))
                    .andExpect(status().isBadRequest());
        }
        verify(assistant, never()).chat(any());
    }

    @Test
    void boundsHistorySizeAndRejectsInvalidSelectedLocation() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat").contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Hello\",\"locationId\":-1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/assistant/chat").contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Hello\",\"history\":[{\"role\":\"assistant\",\"content\":\""
                                + "a".repeat(8001) + "\"}]}"))
                .andExpect(status().isBadRequest());
        verify(assistant, never()).chat(any());
    }

    @Test
    void changingForwardedHeadersCannotBypassTheClientLimit() throws Exception {
        MockMvc guarded = MockMvcBuilders.standaloneSetup(new AssistantController(assistant,
                        new AssistantRequestLimiter(1, java.time.Duration.ofMinutes(10))))
                .setControllerAdvice(new ApiErrorHandler()).build();
        for (int attempt = 0; attempt < 2; attempt++) {
            guarded.perform(post("/api/v1/assistant/chat")
                            .with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
                            .header("X-Forwarded-For", "198.51.100." + attempt)
                            .contentType(APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                    .andExpect(attempt == 0 ? status().isOk() : status().isTooManyRequests());
        }
        verify(assistant, org.mockito.Mockito.times(1)).chat(any());
    }

    @Test
    void returnsSafeServiceUnavailableProblemWhenAiProviderFails() throws Exception {
        when(assistant.chat(any())).thenThrow(new AssistantUnavailableException("upstream key rejected"));

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"What is the latest forecast?\",\"language\":\"en\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("ASSISTANT_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(
                        "The AI assistant is temporarily unavailable. Please try again later."))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("upstream key"))));

        verify(limiter).check(any());
    }

    @Test
    void returnsTooManyRequestsAndDoesNotCallAiWhenRateLimited() throws Exception {
        doThrow(new AssistantRateLimitException()).when(limiter).check(any());

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"What is the latest forecast?\",\"language\":\"en\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("ASSISTANT_RATE_LIMITED"));

        verify(assistant, never()).chat(any());
    }
}
