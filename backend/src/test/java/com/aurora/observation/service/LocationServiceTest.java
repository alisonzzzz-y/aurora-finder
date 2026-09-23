package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.provider.GeocodingProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LocationServiceTest {
    private final GeocodingProvider provider = mock(GeocodingProvider.class);
    private final LocationService service = new LocationService(provider,
            Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void rejectsBlankSearchBeforeCallingProvider() {
        assertThrows(InvalidLocationRequestException.class, () -> service.search("   "));
        verifyNoInteractions(provider);
    }

    @Test
    void trimsSearchAndReusesLocationFromSearchResults() {
        Location dublin = new Location(2964574, "Dublin", "Leinster", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        when(provider.search("Dublin")).thenReturn(List.of(dublin));

        assertEquals(List.of(dublin), service.search("  Dublin  "));
        assertEquals(List.of(dublin), service.search("dublin"));
        assertEquals(dublin, service.get(dublin.id()));
        verify(provider).search("Dublin");
        verifyNoMoreInteractions(provider);
    }

    @Test
    void rejectsNonPositiveLocationId() {
        assertThrows(InvalidLocationRequestException.class, () -> service.get(0));
        verifyNoInteractions(provider);
    }

    @Test
    void missingLocationRemainsNotFound() {
        when(provider.get(12)).thenReturn(Optional.empty());
        assertThrows(LocationNotFoundException.class, () -> service.get(12));
    }
}
