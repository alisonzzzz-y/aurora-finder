package com.aurora.observation.service;

import com.aurora.observation.dto.GeomagneticStormForecastResponse;
import com.aurora.observation.dto.GeomagneticWarningsResponse;
import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.NightOutlook;
import com.aurora.observation.dto.OutlookLevel;
import com.aurora.observation.dto.OutlookReasonCode;
import com.aurora.observation.dto.OutlookResponse;
import com.aurora.observation.dto.RuleStatus;
import com.aurora.observation.dto.ObservationFactsResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservationToolsServiceTest {
    @Test
    void delegatesOnlyToExistingReadOnlySourceServices() {
        LocationService locations = mock(LocationService.class);
        ObservationFactsService facts = mock(ObservationFactsService.class);
        OutlookService outlooks = mock(OutlookService.class);
        KpIndexService kpIndex = mock(KpIndexService.class);
        GeomagneticStormForecastService stormForecast = mock(GeomagneticStormForecastService.class);
        GeomagneticWarningsService warnings = mock(GeomagneticWarningsService.class);
        ObservationToolsService tools = new ObservationToolsService(locations, facts, outlooks, kpIndex, stormForecast, warnings);

        Location dublin = new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        List<Location> candidates = List.of(dublin);
        ObservationFactsResponse localFacts = new ObservationFactsResponse(Instant.EPOCH, null, null, null, null, null, null);
        KpIndexResponse kp = new KpIndexResponse(Instant.EPOCH, "NOAA", List.of());
        GeomagneticStormForecastResponse storms = new GeomagneticStormForecastResponse(
                Instant.EPOCH, Instant.EPOCH, "NOAA", List.of());
        GeomagneticWarningsResponse activeWarnings = new GeomagneticWarningsResponse(
                Instant.EPOCH, "NOAA", List.of(), List.of());
        when(locations.search("Dublin, Ireland")).thenReturn(candidates);
        when(facts.forLocation(dublin.id())).thenReturn(localFacts);
        LocalDate tonight = LocalDate.of(2026, 9, 26);
        NightOutlook selectedNight = new NightOutlook(tonight, "+01:00", Instant.EPOCH, Instant.EPOCH,
                OutlookLevel.INSUFFICIENT_DATA, OutlookReasonCode.RULES_NOT_VALIDATED, null);
        NightOutlook followingNight = new NightOutlook(tonight.plusDays(1), "+01:00", Instant.EPOCH, Instant.EPOCH,
                OutlookLevel.INSUFFICIENT_DATA, OutlookReasonCode.RULES_NOT_VALIDATED, null);
        when(outlooks.forLocation(dublin.id())).thenReturn(new OutlookResponse(dublin, Instant.EPOCH,
                RuleStatus.NOT_VALIDATED, List.of(selectedNight, followingNight)));
        when(kpIndex.latest()).thenReturn(kp);
        when(stormForecast.latest()).thenReturn(storms);
        when(warnings.latest()).thenReturn(activeWarnings);

        assertSame(candidates, tools.searchPlaces("Dublin, Ireland"));
        assertSame(localFacts, tools.getLocalNightFacts(dublin.id()));
        assertSame(selectedNight, tools.getNightOutlook(dublin.id(), tonight));
        UnsupportedNightDateException unsupported = assertThrows(
                UnsupportedNightDateException.class,
                () -> tools.getNightOutlook(dublin.id(), tonight.plusDays(3)));
        assertEquals(tonight.plusDays(3), unsupported.requestedDate());
        assertEquals(List.of(tonight, tonight.plusDays(1)), unsupported.availableDates());
        assertSame(kp, tools.getGlobalKpForecast());
        assertSame(storms, tools.getThreeDayStormForecast());
        assertSame(activeWarnings, tools.getActiveGeomagneticWarnings());
        verify(locations).search("Dublin, Ireland");
        verify(facts).forLocation(dublin.id());
        verify(outlooks, times(2)).forLocation(dublin.id());
    }
}
