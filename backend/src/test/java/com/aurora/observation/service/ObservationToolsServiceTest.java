package com.aurora.observation.service;

import com.aurora.observation.dto.GeomagneticStormForecastResponse;
import com.aurora.observation.dto.GeomagneticWarningsResponse;
import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.ObservationFactsResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservationToolsServiceTest {
    @Test
    void delegatesOnlyToExistingReadOnlySourceServices() {
        LocationService locations = mock(LocationService.class);
        ObservationFactsService facts = mock(ObservationFactsService.class);
        KpIndexService kpIndex = mock(KpIndexService.class);
        GeomagneticStormForecastService stormForecast = mock(GeomagneticStormForecastService.class);
        GeomagneticWarningsService warnings = mock(GeomagneticWarningsService.class);
        ObservationToolsService tools = new ObservationToolsService(locations, facts, kpIndex, stormForecast, warnings);

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
        when(kpIndex.latest()).thenReturn(kp);
        when(stormForecast.latest()).thenReturn(storms);
        when(warnings.latest()).thenReturn(activeWarnings);

        assertSame(candidates, tools.searchPlaces("Dublin, Ireland"));
        assertSame(localFacts, tools.getLocalNightFacts(dublin.id()));
        assertSame(kp, tools.getGlobalKpForecast());
        assertSame(storms, tools.getThreeDayStormForecast());
        assertSame(activeWarnings, tools.getActiveGeomagneticWarnings());
        verify(locations).search("Dublin, Ireland");
        verify(facts).forLocation(dublin.id());
    }
}