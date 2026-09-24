package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.OutlookLevel;
import com.aurora.observation.dto.RuleStatus;
import com.aurora.observation.provider.GeocodingProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.time.Duration.between;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutlookServiceTest {
    @Test
    void usesInjectedClockAndKeepsUnvalidatedNightsInsufficient() {
        GeocodingProvider geocoding = mock(GeocodingProvider.class);
        Location dublin = new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        when(geocoding.get(dublin.id())).thenReturn(Optional.of(dublin));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-24T23:30:00Z"), ZoneOffset.UTC);
        OutlookService service = new OutlookService(locationService(geocoding, fixedClock), fixedClock);

        var response = service.forLocation(dublin.id());

        assertEquals(Instant.parse("2026-09-24T23:30:00Z"), response.generatedAtUtc());
        assertEquals(RuleStatus.NOT_VALIDATED, response.ruleStatus());
        assertEquals(3, response.nights().size());
        assertEquals("2026-09-25", response.nights().getFirst().localDate().toString());
        assertEquals(Instant.parse("2026-09-25T11:00:00Z"),
                response.nights().getFirst().evaluationWindowStartUtc());
        assertEquals(Instant.parse("2026-09-26T11:00:00Z"),
                response.nights().getFirst().evaluationWindowEndUtc());
        assertEquals(OutlookLevel.INSUFFICIENT_DATA, response.nights().getFirst().level());
    }

    @Test
    void keepsCalendarDateWindowsAlignedAcrossDaylightSavingTransition() {
        GeocodingProvider geocoding = mock(GeocodingProvider.class);
        Location dublin = new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        when(geocoding.get(dublin.id())).thenReturn(Optional.of(dublin));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-24T10:30:00Z"), ZoneOffset.UTC);
        OutlookService service = new OutlookService(locationService(geocoding, fixedClock), fixedClock);

        var response = service.forLocation(dublin.id());
        var transitionNight = response.nights().getFirst();
        var followingNight = response.nights().get(1);

        assertEquals("2026-10-24", transitionNight.localDate().toString());
        assertEquals(Instant.parse("2026-10-24T11:00:00Z"), transitionNight.evaluationWindowStartUtc());
        assertEquals(Instant.parse("2026-10-25T12:00:00Z"), transitionNight.evaluationWindowEndUtc());
        assertEquals(25, between(transitionNight.evaluationWindowStartUtc(),
                transitionNight.evaluationWindowEndUtc()).toHours());
        assertEquals(Instant.parse("2026-10-25T12:00:00Z"), followingNight.evaluationWindowStartUtc());
        assertEquals(Instant.parse("2026-10-26T12:00:00Z"), followingNight.evaluationWindowEndUtc());
        assertEquals(24, between(followingNight.evaluationWindowStartUtc(),
                followingNight.evaluationWindowEndUtc()).toHours());
    }

    @Test
    void keepsCalendarDateWindowsAlignedAcrossSpringForwardTransition() {
        GeocodingProvider geocoding = mock(GeocodingProvider.class);
        Location dublin = new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        when(geocoding.get(dublin.id())).thenReturn(Optional.of(dublin));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-28T11:30:00Z"), ZoneOffset.UTC);
        OutlookService service = new OutlookService(locationService(geocoding, fixedClock), fixedClock);

        var night = service.forLocation(dublin.id()).nights().getFirst();

        assertEquals("2026-03-28", night.localDate().toString());
        assertEquals(Instant.parse("2026-03-28T12:00:00Z"), night.evaluationWindowStartUtc());
        assertEquals(Instant.parse("2026-03-29T11:00:00Z"), night.evaluationWindowEndUtc());
        assertEquals(23, between(night.evaluationWindowStartUtc(), night.evaluationWindowEndUtc()).toHours());
    }

    @Test
    void derivesLocalDatesIndependentlyOnOppositeSidesOfTheDateLine() {
        GeocodingProvider geocoding = mock(GeocodingProvider.class);
        Location apia = new Location(4035715, "Apia", "Tuamasaga", "", "Samoa",
                -13.83333, -171.76666, "Pacific/Apia");
        Location pagoPago = new Location(5881576, "Pago Pago", "Eastern", "", "American Samoa",
                -14.27806, -170.7025, "Pacific/Pago_Pago");
        when(geocoding.get(apia.id())).thenReturn(Optional.of(apia));
        when(geocoding.get(pagoPago.id())).thenReturn(Optional.of(pagoPago));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-24T10:30:00Z"), ZoneOffset.UTC);
        OutlookService service = new OutlookService(locationService(geocoding, fixedClock), fixedClock);

        var apiaTonight = service.forLocation(apia.id()).nights().getFirst();
        var pagoPagoTonight = service.forLocation(pagoPago.id()).nights().getFirst();

        assertEquals("2026-09-24", apiaTonight.localDate().toString());
        assertEquals("+13:00", apiaTonight.utcOffsetAtStart());
        assertEquals("2026-09-23", pagoPagoTonight.localDate().toString());
        assertEquals("-11:00", pagoPagoTonight.utcOffsetAtStart());
    }

    private LocationService locationService(GeocodingProvider geocoding, Clock clock) {
        return new LocationService(geocoding, clock, 256, Duration.ofMinutes(10), Duration.ofHours(1));
    }
}
