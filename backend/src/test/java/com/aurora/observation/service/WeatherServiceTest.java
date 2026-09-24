package com.aurora.observation.service;

import com.aurora.observation.dto.WeatherCloudPoint;
import com.aurora.observation.dto.WeatherForecastResponse;
import com.aurora.observation.provider.WeatherProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeatherServiceTest {
    @Test
    void selectsOnlySourcePointsInTheSelectedPlacesTonightWindowAndKeepsGaps() {
        WeatherProvider provider = mock(WeatherProvider.class);
        var source = new WeatherForecastResponse(Instant.parse("2026-09-24T23:30:00Z"),
                Instant.parse("2026-09-25T01:00:00Z"), "MET Norway", 53.333, -6.248,
                List.of(
                        new WeatherCloudPoint(Instant.parse("2026-09-25T10:00:00Z"), 20.0),
                        new WeatherCloudPoint(Instant.parse("2026-09-25T11:00:00Z"), null),
                        new WeatherCloudPoint(Instant.parse("2026-09-25T17:00:00Z"), 75.0),
                        new WeatherCloudPoint(Instant.parse("2026-09-26T11:00:00Z"), 0.0)));
        when(provider.forecast(53.333, -6.248)).thenReturn(source);
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T23:30:00Z"), ZoneOffset.UTC);
        WeatherService service = new WeatherService(provider, clock);

        var result = service.forecast(53.333, -6.248, "Europe/Dublin");

        assertEquals(2, result.cloudForecast().size());
        assertEquals(Instant.parse("2026-09-25T11:00:00Z"), result.cloudForecast().getFirst().validAt());
        assertNull(result.cloudForecast().getFirst().cloudCoverPercent());
        assertEquals(75.0, result.cloudForecast().getLast().cloudCoverPercent());
    }

    @Test
    void rejectsCoordinatesAndUnknownTimezonesBeforeCallingProvider() {
        WeatherProvider provider = mock(WeatherProvider.class);
        WeatherService service = new WeatherService(provider,
                Clock.fixed(Instant.parse("2026-09-24T23:30:00Z"), ZoneOffset.UTC));

        assertThrows(InvalidWeatherRequestException.class, () -> service.forecast(95, 0, "Europe/Dublin"));
        assertThrows(InvalidWeatherRequestException.class, () -> service.forecast(53, 0, "Mars/Nope"));
    }
}
