package com.aurora.observation.controller;

import com.aurora.observation.dto.ObservationFactsResponse;
import com.aurora.observation.monitoring.ObservationHistoryService;
import com.aurora.observation.record.RunRecordStore;
import com.aurora.observation.record.RunRecordUnavailableException;
import com.aurora.observation.service.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ObservationControllerTest {
    private final ObservationFactsService facts = mock(ObservationFactsService.class);
    private final RunRecordStore records = mock(RunRecordStore.class);
    private final ObservationHistoryService history = mock(ObservationHistoryService.class);
    private final ObservationController controller = new ObservationController(mock(LocationService.class),
            mock(OutlookService.class), mock(AuroraMapService.class), mock(KpIndexService.class),
            mock(WeatherService.class), facts, mock(GeomagneticStormForecastService.class),
            mock(GeomagneticWarningsService.class), records, history);
    private final ObservationFactsResponse result = new ObservationFactsResponse(Instant.parse("2026-10-31T00:00:00Z"),
            null, null, null, null, null, null);

    @Test
    void servesFactsWhenTheRunRecordDatabaseIsUnavailable() {
        when(records.begin("PAGE_FACTS", 42L)).thenThrow(new RunRecordUnavailableException(new RuntimeException("down")));
        when(facts.forLocation(42)).thenReturn(result);

        var response = controller.facts(42);

        assertEquals(result.generatedAtUtc(), response.generatedAtUtc());
        assertNull(response.runId());
        verify(records, never()).recordFacts(any(), any());
        verify(history).capture(result);
    }

    @Test
    void dropsTheRunIdWhenEvidenceCannotBeSaved() {
        when(records.begin("PAGE_FACTS", 42L)).thenReturn("run-1");
        when(facts.forLocation(42)).thenReturn(result);
        doThrow(new RunRecordUnavailableException(new RuntimeException("down"))).when(records).recordFacts("run-1", result);

        var response = controller.facts(42);

        assertNull(response.runId());
        verify(records).finish("run-1", "FAILED");
        verify(records, never()).finish("run-1", "COMPLETED");
        verify(records, never()).finish(anyString(), eq("RUNNING"));
    }
}
