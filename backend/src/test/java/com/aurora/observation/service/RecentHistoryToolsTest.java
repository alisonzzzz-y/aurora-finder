package com.aurora.observation.service;
import com.aurora.observation.dto.*;
import com.aurora.observation.monitoring.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RecentHistoryToolsTest {
    private final KpIndexService kp=mock(KpIndexService.class);
    private final ObservationHistoryService history=mock(ObservationHistoryService.class);
    private final Instant now=Instant.parse("2026-10-12T12:00:00Z");
    private ObservationToolsService tools() { return new ObservationToolsService(mock(LocationService.class),mock(ObservationFactsService.class),mock(OutlookService.class),kp,mock(GeomagneticStormForecastService.class),mock(GeomagneticWarningsService.class),history,Clock.fixed(now,ZoneOffset.UTC)); }
    private KpIndexRecord kp(String at,KpIndexType type) {return new KpIndexRecord(Instant.parse(at),7,type,null,AuroraActivityLevel.HIGH,NoaaGeomagneticStormScale.G3);}
    @Test void historySeparatesPastReadingsFromForecastsAndIncompleteIntervals() {
        when(kp.latest()).thenReturn(new KpIndexResponse(now,"NOAA",List.of(kp("2026-10-11T00:00:00Z",KpIndexType.OBSERVED),kp("2026-10-11T03:00:00Z",KpIndexType.ESTIMATED),kp("2026-10-11T06:00:00Z",KpIndexType.PREDICTED),kp("2026-10-12T12:00:00Z",KpIndexType.ESTIMATED),kp("2026-10-01T00:00:00Z",KpIndexType.OBSERVED))));
        var result=tools().getRecentGeomagneticHistory();
        assertEquals(now,result.queriedAtUtc()); assertEquals(2,result.records().size());
        assertEquals("NOAA",result.source());
    }
    private ObservationSnapshot sample(String at) {return new ObservationSnapshot(42,"Dublin","Europe/Dublin",Instant.parse(at),0,"CURRENT",now,now,10.0,true,"HIGH",now,now,"sky-conditions-v2","CURRENT",now);}
    @Test void historicalNightUsesLocalNoonBoundariesAcrossClockChange() {
        var first=sample("2026-10-24T11:00:00Z"); var last=sample("2026-10-25T11:59:00Z");
        when(history.history(42)).thenReturn(new ObservationHistoryService.History("AVAILABLE",7,List.of(sample("2026-10-24T10:59:00Z"),first,last,sample("2026-10-25T12:00:00Z"))));
        assertEquals(List.of(first,last),tools().getLocalHistory(42,LocalDate.of(2026,10,24)).snapshots());
    }
}
