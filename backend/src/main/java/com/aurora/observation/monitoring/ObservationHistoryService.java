package com.aurora.observation.monitoring;

import com.aurora.observation.record.RunRecordStore;
import com.aurora.observation.service.ObservationFactsService;
import com.aurora.observation.dto.ObservationFactsResponse;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@Service
public class ObservationHistoryService {
    private static final Logger LOG=LoggerFactory.getLogger(ObservationHistoryService.class);
    private final RunRecordStore records;
    private final ObservationFactsService facts;
    public record History(String status, int retentionDays, List<ObservationSnapshot> snapshots) {}
    public ObservationHistoryService(RunRecordStore records,ObservationFactsService facts) { this.records=records;this.facts=facts; }
    public void capture(ObservationFactsResponse response) {
        if(!records.historyAvailable()) return;
        try {records.saveHistory(ObservationSnapshot.from(response));}
        catch(RuntimeException failure) {LOG.warn("Public observation snapshot could not be saved");}
    }
    public History history(long locationId) {
        if(locationId<=0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        if(!records.historyAvailable()) return new History("DISABLED",7,List.of());
        try {return new History("AVAILABLE",7,records.history(locationId));}
        catch(RuntimeException failure) {return new History("UNAVAILABLE",7,List.of());}
    }
    // A small fixed watchlist keeps sampling bounded. Searches also record one sample per 15 minutes.
    @Scheduled(initialDelayString="${app.history.initial-delay-ms:60000}",fixedDelayString="${app.history.interval-ms:900000}")
    public void sampleWatchlist() {
        if(!records.historyAvailable()) return;
        for(long id:new long[]{2964574L,2965768L,3413829L}) {
            try {capture(facts.forLocation(id));} catch(RuntimeException failure) {LOG.warn("Watchlist observation unavailable for location {}",id);}
        }
    }
}
