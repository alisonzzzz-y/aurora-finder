package com.aurora.observation.service;

import com.aurora.observation.dto.*;
import com.aurora.observation.provider.OpenAiAssistantProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssistantTimeWindowTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");

    private ObservationFactsResponse facts(String timezone, Instant now, boolean expired) {
        return facts(timezone, now, expired, now.atZone(ZoneId.of(timezone)).toLocalDate());
    }

    private ObservationFactsResponse facts(String timezone, Instant now, boolean expired, LocalDate firstNight) {
        var zone = ZoneId.of(timezone);
        var today = now.atZone(zone).toLocalDate();
        var place = new Location(42, "Dublin", "Leinster", "Dublin City", "Ireland", 53.33, -6.25, timezone);
        var nights = java.util.stream.IntStream.range(0,3).mapToObj(i -> {
            var date = firstNight.plusDays(i);
            return new NightOutlook(date, date.atTime(12,0).atZone(zone).getOffset().toString(),
                    date.atTime(12,0).atZone(zone).toInstant(), date.plusDays(1).atTime(12,0).atZone(zone).toInstant(),
                    OutlookLevel.INSUFFICIENT_DATA, OutlookReasonCode.RULES_NOT_VALIDATED, null);
        }).toList();
        var points = List.of(
                new WeatherCloudPoint(today.atTime(9,0).atZone(zone).toInstant(), 91.0),
                new WeatherCloudPoint(today.atTime(20,0).atZone(zone).toInstant(), 21.5),
                new WeatherCloudPoint(today.plusDays(1).atTime(2,0).atZone(zone).toInstant(), 12.3),
                new WeatherCloudPoint(today.plusDays(1).atTime(20,0).atZone(zone).toInstant(), 82.0),
                new WeatherCloudPoint(today.plusDays(2).atTime(2,0).atZone(zone).toInstant(), 75.0));
        var weather = new WeatherForecastResponse(now, expired ? now.minusSeconds(1) : now.plusSeconds(3600), "MET Norway", 53.33, -6.25, points);
        var cloud = new SourceFact<>(expired ? FactFetchStatus.EXPIRED : FactFetchStatus.CURRENT,
                FactTimeScope.THREE_LOCAL_NIGHTS, now, null, null, now, now.plusSeconds(259200),
                "MET Norway", "https://example.test/weather", null, weather);
        return new ObservationFactsResponse(now, new OutlookResponse(place, now, RuleStatus.NOT_VALIDATED, nights),
                null, cloud, null, null, FactFetchStatus.CURRENT);
    }

    private AssistantChatResponse chat(String request, List<AssistantMessage> history, String answer, ObservationFactsResponse facts) {
        var provider = mock(OpenAiAssistantProvider.class);
        var tools = mock(ObservationToolsService.class);
        when(tools.getLocalNightFacts(42)).thenReturn(facts);
        var call = mapper.createObjectNode().put("type", "function_call").put("name", "get_local_night_facts")
                .put("call_id", "cloud").put("arguments", mapper.createObjectNode().put("location_id",42).toString());
        var toolResponse = mapper.createObjectNode().set("output", mapper.createArrayNode().add(call));
        var finalResponse = mapper.createObjectNode().set("output", mapper.createArrayNode().add(
                mapper.createObjectNode().put("type","message").set("content",mapper.createArrayNode().add(
                        mapper.createObjectNode().put("type","output_text").put("text",answer)))));
        when(provider.respond(anyString(), any(), any())).thenReturn(toolResponse, finalResponse);
        return new AssistantService(provider,tools,mapper).chat(new AssistantChatRequest(request,"en",42L,history));
    }

    @Test void wrongToolDateTellsTheModelWhichDateTheUserAskedFor() {
        var provider = mock(OpenAiAssistantProvider.class);
        var tools = mock(ObservationToolsService.class);
        when(tools.getLocalNightFacts(42)).thenReturn(facts("Europe/Dublin",NOW,false));
        var factsCall = mapper.createObjectNode().put("type","function_call").put("name","get_local_night_facts")
                .put("call_id","facts").put("arguments","{\"location_id\":42}");
        var cloudCall = mapper.createObjectNode().put("type","function_call").put("name","get_night_cloud_forecast")
                .put("call_id","cloud").put("arguments","{\"location_id\":42,\"local_date\":\"2026-10-27\"}");
        var answer = mapper.createObjectNode().set("output", mapper.createArrayNode().add(
                mapper.createObjectNode().put("type","message").set("content",mapper.createArrayNode().add(
                        mapper.createObjectNode().put("type","output_text").put("text","Checked tonight.")))));
        var inputs = org.mockito.ArgumentCaptor.forClass(ArrayNode.class);
        when(provider.respond(anyString(), any(), any())).thenReturn(
                mapper.createObjectNode().set("output", mapper.createArrayNode().add(factsCall)),
                mapper.createObjectNode().set("output", mapper.createArrayNode().add(cloudCall)), answer);

        new AssistantService(provider,tools,mapper).chat(new AssistantChatRequest("Clouds tonight?","en",42L,List.of()));

        verify(provider, times(3)).respond(anyString(), inputs.capture(), any());
        assertTrue(inputs.getAllValues().get(2).toString().contains("Use the user's requested local date: 2026-10-26"));
    }

    @Test void chineseCalendarFollowupOverridesEarlierTonight() {
        var request = AssistantCloudEvidence.requestTime("10月27日呢？", List.of(new AssistantMessage("user", "今晚呢？")));
        var window = AssistantCloudEvidence.select(facts("Europe/Dublin",NOW,false),request,null);
        assertEquals(LocalDate.of(2026,10,27),window.localDate());
        assertEquals(82.0,window.points().getFirst().cloudCoverPercent());
    }

    @Test void resolvedPlaceIsReturnedForSubsequentQuestions() {
        var response = chat("Cloud cover tonight?",List.of(),"Clouds vary.",facts("Europe/Dublin",NOW,false));
        assertEquals(42L,response.selectedLocationId());
    }

    @Test void exactDateToolReturnsOnlyChosenNightToModel() {
        var provider = mock(OpenAiAssistantProvider.class);
        var tools = mock(ObservationToolsService.class);
        when(tools.getLocalNightFacts(42)).thenReturn(facts("Europe/Dublin",NOW,false));
        var count = new java.util.concurrent.atomic.AtomicInteger();
        when(provider.respond(anyString(), any(), any())).thenAnswer(invocation -> {
            int turn = count.getAndIncrement();
            if (turn == 2) {
                ArrayNode input = invocation.getArgument(1);
                String result = input.get(input.size()-1).path("output").asText();
                var window = mapper.readTree(result).path("assistantCloudWindow");
                assertEquals("2026-10-27",window.path("localDate").asText());
                assertEquals(2,window.path("localSamples").size());
                assertEquals(82.0,window.path("localSamples").get(0).path("cloudCoverPercent").asDouble());
                return mapper.createObjectNode().set("output",mapper.createArrayNode().add(
                    mapper.createObjectNode().put("type","message").set("content",mapper.createArrayNode().add(
                        mapper.createObjectNode().put("type","output_text").put("text","2026-10-27 20:00: 82%.")))));
            }
            var args = mapper.createObjectNode().put("location_id",42);
            if (turn == 1) args.put("local_date","2026-10-27");
            return mapper.createObjectNode().set("output",mapper.createArrayNode().add(mapper.createObjectNode()
                .put("type","function_call").put("call_id","step"+turn)
                .put("name",turn==0 ? "get_local_night_facts" : "get_night_cloud_forecast")
                .put("arguments",args.toString())));
        });
        var response = new AssistantService(provider,tools,mapper).chat(new AssistantChatRequest("Cloud cover tomorrow night?","en",42L,List.of()));
        assertEquals("2026-10-27 20:00: 82%.",response.answer());
        assertEquals(3,count.get());
    }

    @Test void chineseExplicitYearMustNotBecomeTheCurrentYear() {
        var window = AssistantCloudEvidence.select(facts("Europe/Dublin",NOW,false),"2030年10月26日",null);
        assertEquals(LocalDate.of(2030,10,26),window.localDate());
        assertEquals("OUTSIDE_FORECAST_WINDOW",window.status());
        assertTrue(window.points().isEmpty());
    }

    @Test void englishCalendarFollowupOverridesEarlierTonight() {
        String request = AssistantCloudEvidence.requestTime("What about October 27?",List.of(new AssistantMessage("user","tonight")));
        assertEquals(LocalDate.of(2026,10,27),AssistantCloudEvidence.select(facts("Europe/Dublin",NOW,false),request,null).localDate());
        assertEquals(LocalDate.of(2030,1,1),AssistantCloudEvidence.requestedDate("Jan 1, 2030",LocalDate.of(2026,10,26)));
    }

    @Test void fallbackForTonightNeverIncludesEarlierMorningOrTomorrowEvening() {
        var answer = chat("Cloud cover tonight?", List.of(), "At 20:00 cloud cover is 82%.", facts("Europe/Dublin",NOW,false)).answer();
        assertTrue(answer.contains("2026-10-26 20:00 Z: 21.5%"), answer);
        assertTrue(answer.contains("2026-10-27 02:00 Z: 12.3%"), answer);
        assertFalse(answer.contains("91%"));
        assertFalse(answer.contains("82%"));
    }

    @Test void followupLocationSelectionRetainsOriginalNight() {
        var history = List.of(new AssistantMessage("user","都柏林今晚云量怎么样？"),new AssistantMessage("assistant","请选择地点"));
        var answer = chat("Dublin, Ireland",history,"At 09:00 cloud cover is 91%.",facts("Europe/Dublin",NOW,false)).answer();
        assertTrue(answer.contains("2026-10-26 20:00 Z: 21.5%"), answer);
        assertFalse(answer.contains("91%"));
    }

    @Test void followupTomorrowOverridesPreviousTonight() {
        var history = List.of(new AssistantMessage("user","Cloud cover tonight?"),new AssistantMessage("assistant","Here are tonight's clouds."));
        var answer = chat("那明晚呢？", history, "云量20时21.5%。",facts("Europe/Dublin",NOW,false)).answer();
        assertTrue(answer.contains("2026-10-27 20:00 Z: 82%"),answer);
        assertFalse(answer.contains("21.5%"));
    }

    @Test void incorrectExplicitDateIsNotAcceptedEvenWithCorrectHourAndValue() {
        var answer = chat("Cloud cover tonight?",List.of(),"2026-10-27 20:00 cloud cover is 21.5%.",facts("Europe/Dublin",NOW,false)).answer();
        assertFalse(answer.contains("2026-10-27 20:00 cloud"));
        assertTrue(answer.contains("2026-10-26 20:00 Z: 21.5%"));
    }

    @Test void exactRequestedDateHasNoSubstituteIfOutsideForecast() {
        var answer = chat("Cloud cover on 2030-01-01?",List.of(),"At 20:00 cloud cover is 21.5%.",facts("Europe/Dublin",NOW,false)).answer();
        assertTrue(answer.contains("No cloud forecast is available for the requested window."));
        assertFalse(answer.contains("21.5%"));
    }

    @Test void expiredSourceCannotValidateOrSupplyFallbackReadings() {
        var answer = chat("Cloud cover tonight?",List.of(),"At 20:00 cloud cover is 21.5%.",facts("Europe/Dublin",NOW,true)).answer();
        assertTrue(answer.contains("No cloud forecast is available"));
        assertFalse(answer.contains("21.5%"));
    }

    @Test void localCalendarDateUsesPlaceInsteadOfServerUtcDate() {
        var facts = facts("Pacific/Apia",Instant.parse("2026-10-26T23:00:00Z"),false);
        var window = AssistantCloudEvidence.select(facts,"tonight",null);
        assertEquals(LocalDate.of(2026,10,27),window.localDate());
        assertEquals(2,window.points().size());
        assertTrue(AssistantCloudEvidence.localSamples(window).getFirst().get("localTime").toString().contains("+13:00"));
    }

    @Test void afterMidnightTonightIsTheNightInProgress() {
        // The outlook keeps the evening's date until dawn, so 00:30 on 26 October belongs to the 25 October night.
        var facts = facts("Europe/Dublin",Instant.parse("2026-10-26T00:30:00Z"),false,LocalDate.of(2026,10,25));
        var tonight = AssistantCloudEvidence.select(facts,"tonight",null);
        assertEquals(LocalDate.of(2026,10,25),tonight.localDate());
        assertEquals("AVAILABLE",tonight.status());
        assertEquals(91.0,tonight.points().getFirst().cloudCoverPercent());
        var tomorrow = AssistantCloudEvidence.select(facts,"tomorrow night",null);
        assertEquals(LocalDate.of(2026,10,26),tomorrow.localDate());
        assertEquals(21.5,tomorrow.points().getFirst().cloudCoverPercent());
    }

    @Test void autumnDstNightHasTwentyFiveHoursAndSpringHasTwentyThree() {
        var autumn=AssistantCloudEvidence.select(facts("Europe/Dublin",Instant.parse("2026-10-24T08:00:00Z"),false),"tonight",null);
        var spring=AssistantCloudEvidence.select(facts("Europe/Dublin",Instant.parse("2026-03-28T08:00:00Z"),false),"tonight",null);
        assertEquals(25,Duration.between(autumn.startUtc(),autumn.endUtc()).toHours());
        assertEquals(23,Duration.between(spring.startUtc(),spring.endUtc()).toHours());
    }

    @Test void multipleDatesRequireExplicitWindowSelection() {
        var facts=facts("Europe/Dublin",NOW,false);
        assertEquals("DATE_SELECTION_REQUIRED",AssistantCloudEvidence.select(facts,"tonight and tomorrow night",null).status());
        var tomorrow=AssistantCloudEvidence.select(facts,"tonight and tomorrow night",LocalDate.of(2026,10,27));
        assertEquals(82.0,tomorrow.points().getFirst().cloudCoverPercent());
    }

    @Test void modelCannotSelectAnotherNightForExplicitTonightQuestion() {
        assertThrows(IllegalArgumentException.class,()->AssistantCloudEvidence.select(facts("Europe/Dublin",NOW,false),"tonight",LocalDate.of(2026,10,27)));
    }

    @Test void currentConditionsDoNotReuseEarlierNightContext() {
        var history=List.of(new AssistantMessage("user","Cloud cover tomorrow night?"));
        var scope=AssistantCloudEvidence.requestTime("现在呢？",history);
        var window=AssistantCloudEvidence.select(facts("Europe/Dublin",NOW,false),scope,null);
        assertNull(window.localDate());
        assertEquals(91.0,window.points().getFirst().cloudCoverPercent());
    }

    @Test void dayBeforeYesterdayOverridesEarlierTonightInBothLanguages() {
        var history = List.of(new AssistantMessage("user", "Cloud cover tonight?"));
        for (String reply : List.of("前天呢？", "前夜呢？", "前晚呢？", "Day before yesterday?", "day  before  yesterday")) {
            String scope = AssistantCloudEvidence.requestTime(reply, history);
            assertEquals(reply, scope);
            var window = AssistantCloudEvidence.select(facts("Europe/Dublin", NOW, false), scope, null);
            assertEquals(LocalDate.of(2026, 10, 24), window.localDate(), reply);
            assertEquals("OUTSIDE_FORECAST_WINDOW", window.status(), reply);
            assertTrue(window.points().isEmpty(), reply);
        }
    }

    @Test void todayOverridesPreviousTomorrow() {
        String scope = AssistantCloudEvidence.requestTime("What about today?",
                List.of(new AssistantMessage("user", "Tomorrow night?")));
        assertEquals(LocalDate.of(2026, 10, 26),
                AssistantCloudEvidence.select(facts("Europe/Dublin", NOW, false), scope, null).localDate());
    }

    @Test void timeWordsInsideOtherWordsDoNotReplaceTheRequestedNight() {
        var history = List.of(new AssistantMessage("user", "Tomorrow night?"));
        assertEquals("Tomorrow night?", AssistantCloudEvidence.requestTime("What about snow?", history));
        assertEquals("Tomorrow night?", AssistantCloudEvidence.requestTime("The conditions are unknown", history));
    }

    @Test void overlappingRelativePhrasesRemainUnambiguous() {
        var today = LocalDate.of(2026, 10, 26);
        for (String text : List.of("day after tomorrow", "day  after  tomorrow", "后天", "后晚")) {
            assertEquals(today.plusDays(2), AssistantCloudEvidence.requestedDate(text, today), text);
        }
        assertNull(AssistantCloudEvidence.requestedDate("day before yesterday and yesterday", today));
        assertNull(AssistantCloudEvidence.requestedDate("today and tomorrow", today));
    }

}
