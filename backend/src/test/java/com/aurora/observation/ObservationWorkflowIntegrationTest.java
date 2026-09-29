package com.aurora.observation;

import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.OvationForecast;
import com.aurora.observation.dto.OvationGridPoint;
import com.aurora.observation.dto.WeatherCloudPoint;
import com.aurora.observation.dto.WeatherForecastResponse;
import com.aurora.observation.provider.GeocodingProvider;
import com.aurora.observation.provider.OpenAiAssistantProvider;
import com.aurora.observation.provider.OvationProvider;
import com.aurora.observation.provider.ProviderFailure;
import com.aurora.observation.provider.ProviderUnavailableException;
import com.aurora.observation.provider.WeatherProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ObservationWorkflowIntegrationTest.FixedTime.class)
class ObservationWorkflowIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final Location DUBLIN = new Location(2964574, "Dublin", "Leinster", "Dublin City",
            "Ireland", 53.33306, -6.24889, "Europe/Dublin");
    private static final Location US_DUBLIN = new Location(4192205, "Dublin", "Georgia", "Laurens",
            "United States", 32.54044, -82.90375, "America/New_York");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean GeocodingProvider geocoding;
    @MockitoBean OvationProvider ovation;
    @MockitoBean WeatherProvider weather;
    @MockitoBean OpenAiAssistantProvider openAi;

    @BeforeEach
    void externalSources() {
        when(geocoding.search("Dublin")).thenReturn(List.of(DUBLIN, US_DUBLIN));
        when(geocoding.get(DUBLIN.id())).thenReturn(Optional.of(DUBLIN));
        when(ovation.latest()).thenReturn(new OvationForecast(NOW.minusSeconds(600), NOW.plusSeconds(3600),
                "NOAA OVATION", List.of(new OvationGridPoint(-6, 53, 30), new OvationGridPoint(120, 70, 65))));
        when(weather.forecast(DUBLIN.latitude(), DUBLIN.longitude())).thenReturn(
                new WeatherForecastResponse(NOW, NOW.plusSeconds(3600), "MET Norway",
                        DUBLIN.latitude(), DUBLIN.longitude(), List.of(
                        new WeatherCloudPoint(NOW.plusSeconds(900), 45.0),
                        new WeatherCloudPoint(NOW.plusSeconds(86400), 10.0))));
        when(openAi.model()).thenReturn("test-model");
    }

    @Test
    void searchSelectionMapAndAssistantUseTheSameLocalFacts() throws Exception {
        JsonNode candidates = readGet("/api/v1/locations?q=Dublin");
        assertEquals(2, candidates.size());
        assertEquals("Ireland", candidates.get(0).path("country").asText());
        assertEquals("United States", candidates.get(1).path("country").asText());
        long selectedId = candidates.get(0).path("id").asLong();

        JsonNode facts = readGet("/api/v1/facts/" + selectedId);
        assertEquals("Europe/Dublin", facts.path("outlook").path("location").path("timezone").asText());
        JsonNode nights = facts.path("outlook").path("nights");
        assertEquals(3, nights.size());
        assertEquals("2026-09-28", nights.get(0).path("localDate").asText());
        assertEquals("2026-09-30", nights.get(2).path("localDate").asText());
        assertEquals("+01:00", nights.get(0).path("utcOffsetAtStart").asText());
        for (JsonNode night : nights) assertEquals("INSUFFICIENT_DATA", night.path("level").asText());
        assertEquals("NOT_VALIDATED", facts.path("outlook").path("ruleStatus").asText());
        assertEquals("CURRENT", facts.path("sourceStatus").asText());
        assertEquals(1, facts.path("cloudForecast").path("data").path("cloudForecast").size());

        JsonNode map = readGet("/api/v1/aurora-map");
        assertEquals("CURRENT", map.path("status").asText());
        assertEquals(map.path("forecastTime"), facts.path("auroraActivity").path("data").path("forecastTime"));
        assertEquals(30, facts.path("auroraActivity").path("data").path("modelValue").asInt());
        assertEquals(2, map.path("points").size());

        assertEquals(facts, askAssistantAndCaptureFacts(selectedId));
    }

    @Test
    void auroraTimeoutRemainsPartialInBothPageApiAndAssistantTool() throws Exception {
        when(ovation.latest())
                .thenThrow(new ProviderUnavailableException(ProviderFailure.TIMEOUT, "private NOAA source detail"));

        JsonNode facts = readGet("/api/v1/facts/" + DUBLIN.id());
        assertEquals("PARTIAL", facts.path("sourceStatus").asText());
        assertEquals("UNAVAILABLE", facts.path("auroraActivity").path("status").asText());
        assertEquals("TIMEOUT", facts.path("auroraActivity").path("failureCode").asText());
        assertFalse(facts.path("auroraActivity").hasNonNull("data"));
        assertEquals("CURRENT", facts.path("cloudForecast").path("status").asText());
        assertEquals("CURRENT", facts.path("solarDarkness").path("status").asText());
        assertFalse(facts.toString().contains("private NOAA source detail"));

        assertEquals(facts, askAssistantAndCaptureFacts(DUBLIN.id()));
    }

    @Test
    void weatherTimeoutRemainsPartialInBothPageApiAndAssistantTool() throws Exception {
        when(weather.forecast(DUBLIN.latitude(), DUBLIN.longitude()))
                .thenThrow(new ProviderUnavailableException(ProviderFailure.TIMEOUT, "private source detail"));
        JsonNode facts = readGet("/api/v1/facts/" + DUBLIN.id());
        assertEquals("PARTIAL", facts.path("sourceStatus").asText());
        assertEquals("CURRENT", facts.path("auroraActivity").path("status").asText());
        assertEquals("CURRENT", facts.path("solarDarkness").path("status").asText());
        assertEquals("UNAVAILABLE", facts.path("cloudForecast").path("status").asText());
        assertEquals("TIMEOUT", facts.path("cloudForecast").path("failureCode").asText());
        assertFalse(facts.path("cloudForecast").hasNonNull("data"));
        assertFalse(facts.toString().contains("private source detail"));
        assertEquals(facts, askAssistantAndCaptureFacts(DUBLIN.id()));
    }

    private JsonNode askAssistantAndCaptureFacts(long selectedId) throws Exception {
        AtomicReference<JsonNode> toolFacts = new AtomicReference<>();
        when(openAi.respond(anyString(), any(), any())).thenAnswer(invocation -> {
            JsonNode input = invocation.getArgument(1);
            for (JsonNode item : input) {
                if ("function_call_output".equals(item.path("type").asText())) {
                    toolFacts.set(mapper.readTree(item.path("output").asText()));
                    return mapper.readTree("""
                            {"output":[{"type":"message","content":[{"type":"output_text","text":"已读取当地数据。"}]}]}
                            """);
                }
            }
            var call = mapper.createObjectNode().put("type", "function_call")
                    .put("call_id", "call_local_facts").put("name", "get_local_night_facts")
                    .put("arguments", mapper.createObjectNode().put("location_id", selectedId).toString());
            return mapper.createObjectNode().set("output", mapper.createArrayNode().add(call));
        });
        var request = mapper.createObjectNode().put("message", "查询所选地点今晚的云量")
                .put("language", "zh").put("locationId", selectedId);
        JsonNode response = mapper.readTree(mvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("已读取当地数据。", response.path("answer").asText());
        verify(openAi, times(2)).respond(anyString(), any(), any());
        assertNotNull(toolFacts.get());
        return toolFacts.get();
    }

    private JsonNode readGet(String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary
        Clock workflowClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
