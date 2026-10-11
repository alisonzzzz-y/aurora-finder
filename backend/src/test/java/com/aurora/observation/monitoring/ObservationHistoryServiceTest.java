package com.aurora.observation.monitoring;

import com.aurora.observation.record.RunRecordStore;
import com.aurora.observation.service.ObservationFactsService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObservationHistoryServiceTest {
    @Test void disabledStorageDoesNotFetchWatchlist() {
        var records=mock(RunRecordStore.class);
        var facts=mock(ObservationFactsService.class);
        var history=new ObservationHistoryService(records,facts);
        assertEquals("DISABLED",history.history(2964574).status());
        history.sampleWatchlist();
        verifyNoInteractions(facts);
    }
    @Test void databaseFailureIsDistinctFromEmptyHistory() {
        var records=mock(RunRecordStore.class);
        when(records.historyAvailable()).thenReturn(true);
        when(records.history(2964574)).thenThrow(new IllegalStateException("unavailable"));
        var history=new ObservationHistoryService(records,mock(ObservationFactsService.class));
        assertEquals("UNAVAILABLE",history.history(2964574).status());
        assertTrue(history.history(2964574).snapshots().isEmpty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->history.history(0));
    }
}
