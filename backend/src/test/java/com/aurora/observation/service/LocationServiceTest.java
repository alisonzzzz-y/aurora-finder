package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.provider.GeocodingProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LocationServiceTest {
    private final GeocodingProvider provider = mock(GeocodingProvider.class);
    private final LocationService service = new LocationService(provider,
            Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC), 256,
            Duration.ofMinutes(10), Duration.ofHours(1));

    @Test
    void rejectsBlankSearchBeforeCallingProvider() {
        assertThrows(InvalidLocationRequestException.class, () -> service.search("   "));
        verifyNoInteractions(provider);
    }

    @Test
    void trimsSearchAndReusesLocationFromSearchResults() {
        Location dublin = new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        when(provider.search("Dublin")).thenReturn(List.of(dublin));

        assertEquals(List.of(dublin), service.search("  Dublin  "));
        assertEquals(List.of(dublin), service.search("dublin"));
        assertEquals(dublin, service.get(dublin.id()));
        verify(provider).search("Dublin");
        verifyNoMoreInteractions(provider);
    }

    @Test
    void filtersFuzzyProviderResultsThatDoNotMatchThePlaceName() {
        Location dublin = new Location(2964574, "Dublin", "Leinster", "Dublin City", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        Location dublinPike = new Location(2964787, "Dublin Pike", "Munster", "County Cork", "Ireland",
                51.9200, -8.4500, "Europe/Dublin");
        Location unrelatedFuzzyMatch = new Location(4218450, "Resaca", "Georgia", "Gordon", "United States",
                34.58036, -84.94328, "America/New_York");
        when(provider.search("Dublin")).thenReturn(List.of(dublin, dublinPike, unrelatedFuzzyMatch));

        assertEquals(List.of(dublin, dublinPike), service.search("Dublin"));
        verify(provider).search("Dublin");
        verify(provider, org.mockito.Mockito.never()).get(unrelatedFuzzyMatch.id());
    }

    @Test
    void matchesEveryQueryWordAgainstPlaceAndAdministrativeNames() {
        Location ireland = new Location(2964574, "Dublin", "Leinster", "Dublin City", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        Location georgia = new Location(4192205, "Dublin", "Georgia", "Laurens", "United States",
                32.54044, -82.90375, "America/New_York");
        when(provider.search("Dublin Ireland")).thenReturn(List.of());
        when(provider.search("dublin")).thenReturn(List.of(ireland, georgia));

        assertEquals(List.of(ireland), service.search("Dublin Ireland"));
        verify(provider).search("Dublin Ireland");
        verify(provider).search("dublin");
    }

    @Test
    void supportsCountryBeforeCityWhenTheGeocoderOnlyFindsTheCityAlone() {
        Location ireland = new Location(2964574, "Dublin", "Leinster", "Dublin City", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        Location georgia = new Location(4192205, "Dublin", "Georgia", "Laurens", "United States",
                32.54044, -82.90375, "America/New_York");
        when(provider.search("Ireland Dublin")).thenReturn(List.of());
        when(provider.search("ireland")).thenReturn(List.of());
        when(provider.search("dublin")).thenReturn(List.of(ireland, georgia));

        assertEquals(List.of(ireland), service.search("Ireland Dublin"));
    }

    @Test
    void misspelledQueryDoesNotReturnUnrelatedFuzzyCandidates() {
        Location dublin = new Location(2964574, "Dublin", "Leinster", "Dublin City", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        when(provider.search("Dublni")).thenReturn(List.of(dublin));

        assertTrue(service.search("Dublni").isEmpty());
    }

    @Test
    void coalescesConcurrentSearchesWithNormalizedQueryKeys() throws Exception {
        Location dublin = new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch allowProviderToFinish = new CountDownLatch(1);
        when(provider.search("Dublin")).thenAnswer(invocation -> {
            providerStarted.countDown();
            assertTrue(allowProviderToFinish.await(2, TimeUnit.SECONDS));
            return List.of(dublin);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<Location>> first = executor.submit(() -> service.search("Dublin"));
            assertTrue(providerStarted.await(2, TimeUnit.SECONDS));
            Future<List<Location>> second = executor.submit(() -> service.search(" dublin "));

            assertFalse(second.isDone());
            allowProviderToFinish.countDown();
            assertEquals(List.of(dublin), first.get(2, TimeUnit.SECONDS));
            assertEquals(List.of(dublin), second.get(2, TimeUnit.SECONDS));
            verify(provider).search("Dublin");
            verifyNoMoreInteractions(provider);
        } finally {
            allowProviderToFinish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void coalescesConcurrentReadsOfTheSameSelectedLocation() throws Exception {
        Location dublin = new Location(2964574, "Dublin", "Leinster", "County Dublin", "Ireland",
                53.33306, -6.24889, "Europe/Dublin");
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch allowProviderToFinish = new CountDownLatch(1);
        when(provider.get(dublin.id())).thenAnswer(invocation -> {
            providerStarted.countDown();
            assertTrue(allowProviderToFinish.await(2, TimeUnit.SECONDS));
            return Optional.of(dublin);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Location> first = executor.submit(() -> service.get(dublin.id()));
            assertTrue(providerStarted.await(2, TimeUnit.SECONDS));
            Future<Location> second = executor.submit(() -> service.get(dublin.id()));

            assertFalse(second.isDone());
            allowProviderToFinish.countDown();
            assertEquals(dublin, first.get(2, TimeUnit.SECONDS));
            assertEquals(dublin, second.get(2, TimeUnit.SECONDS));
            verify(provider).get(dublin.id());
            verifyNoMoreInteractions(provider);
        } finally {
            allowProviderToFinish.countDown();
            executor.shutdownNow();
        }
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
