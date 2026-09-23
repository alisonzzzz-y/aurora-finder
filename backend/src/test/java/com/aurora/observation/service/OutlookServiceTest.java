package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.OutlookLevel;
import com.aurora.observation.dto.RuleStatus;
import com.aurora.observation.provider.GeocodingProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutlookServiceTest {
    @Test
    void usesInjectedClockAndKeepsUnvalidatedNightsInsufficient() {
        GeocodingProvider geocoding = mock(GeocodingProvider.class);
        Location dublin = new Location(2964574, "Dublin", "Leinster", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        when(geocoding.get(dublin.id())).thenReturn(Optional.of(dublin));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-24T23:30:00Z"), ZoneOffset.UTC);
        OutlookService service = new OutlookService(new LocationService(geocoding, fixedClock), fixedClock);

        var response = service.forLocation(dublin.id());

        assertEquals(Instant.parse("2026-09-24T23:30:00Z"), response.generatedAtUtc());
        assertEquals(RuleStatus.NOT_VALIDATED, response.ruleStatus());
        assertEquals(3, response.nights().size());
        assertEquals("2026-09-25", response.nights().getFirst().localDate().toString());
        assertEquals(OutlookLevel.INSUFFICIENT_DATA, response.nights().getFirst().level());
    }
}
