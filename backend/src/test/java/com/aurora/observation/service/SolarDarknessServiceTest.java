package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.SolarDarkness.Status;
import com.aurora.observation.dto.SolarDarkness.Threshold;
import net.e175.klaus.solarpositioning.DeltaT;
import net.e175.klaus.solarpositioning.SPA;
import net.e175.klaus.solarpositioning.SPA.Horizon;
import net.e175.klaus.solarpositioning.SunriseResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolarDarknessServiceTest {
    private final SolarDarknessService service = new SolarDarknessService();

    @Test
    void matchesPublishedNrelSpaReferencePosition() {
        ZonedDateTime dateTime = ZonedDateTime.parse("2003-10-17T12:30:30-07:00[America/Denver]");

        var position = SPA.calculateSolarPosition(dateTime, 39.742476, -105.1786, 1830.14,
                67, 820, 11);

        assertEquals(50.11162, position.zenithAngle(), 0.0003);
        assertEquals(194.34024, position.azimuth(), 0.0003);
    }

    @Test
    void findsLocalDarknessOnWinterNightAtTromsoAndNoAstronomicalNightInMidsummer() {
        Location tromso = location(69.6492, 18.9553, "Europe/Oslo");
        var winter = service.forWindow(tromso,
                Instant.parse("2026-12-21T11:00:00Z"), Instant.parse("2026-12-22T11:00:00Z"));
        var winterAstronomical = winter.thresholds().stream()
                .filter(item -> item.threshold() == Threshold.ASTRONOMICAL_TWILIGHT).findFirst().orElseThrow();
        assertEquals(Status.INTERVALS_FOUND, winterAstronomical.status());
        assertEquals(1, winterAstronomical.intervals().size());
        assertTrue(winterAstronomical.intervals().getFirst().startUtc()
                .isBefore(Instant.parse("2026-12-21T23:00:00Z")));
        assertTrue(winterAstronomical.intervals().getFirst().endUtc()
                .isAfter(Instant.parse("2026-12-22T02:00:00Z")));

        var summer = service.forWindow(tromso,
                Instant.parse("2026-06-21T10:00:00Z"), Instant.parse("2026-06-22T10:00:00Z"));
        assertEquals(Status.NO_INTERVAL, summer.thresholds().stream()
                .filter(item -> item.threshold() == Threshold.CIVIL_TWILIGHT).findFirst().orElseThrow().status());
    }

    @Test
    void recognizesContinuousPolarNightAndLibraryPolarDayResult() {
        Location highArctic = location(85.0, 0.0, "UTC");
        var polarNight = service.forWindow(highArctic,
                Instant.parse("2026-12-21T12:00:00Z"), Instant.parse("2026-12-22T12:00:00Z"));
        assertEquals(Status.CONTINUOUS, polarNight.thresholds().stream()
                .filter(item -> item.threshold() == Threshold.ASTRONOMICAL_TWILIGHT).findFirst().orElseThrow().status());

        SunriseResult summerResult = SPA.calculateSunriseTransitSet(
                ZonedDateTime.of(2026, 6, 21, 0, 0, 0, 0, ZoneId.of("UTC")), 85, 0,
                DeltaT.estimate(LocalDate.of(2026, 6, 21)), Horizon.ASTRONOMICAL_TWILIGHT);
        assertTrue(summerResult instanceof SunriseResult.AllDay);
    }

    @Test
    void returnsIntervalsInUtcWhileWindowsRemainBoundedToTheSuppliedDateLineRange() {
        Location apia = location(-13.83333, -171.76666, "Pacific/Apia");
        Instant start = Instant.parse("2026-09-24T11:00:00Z");
        Instant end = Instant.parse("2026-09-25T11:00:00Z");

        var result = service.forWindow(apia, start, end);

        result.thresholds().forEach(window -> window.intervals().forEach(interval -> {
            assertTrue(!interval.startUtc().isBefore(start));
            assertTrue(!interval.endUtc().isAfter(end));
        }));
    }

    @Test
    void respectsShortAndLongLocalCalendarWindowsAcrossDublinDstChanges() {
        Location dublin = location(53.33306, -6.24889, "Europe/Dublin");
        ZoneId zone = ZoneId.of(dublin.timezone());
        for (var sample : new Object[][] {
                { LocalDate.of(2026, 3, 28), 23L },
                { LocalDate.of(2026, 10, 24), 25L }
        }) {
            LocalDate date = (LocalDate) sample[0];
            long expectedHours = (long) sample[1];
            Instant start = date.atTime(LocalTime.NOON).atZone(zone).toInstant();
            Instant end = date.plusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant();

            var result = service.forWindow(dublin, start, end);

            assertEquals(expectedHours, java.time.Duration.between(start, end).toHours());
            result.thresholds().forEach(window -> window.intervals().forEach(interval -> {
                assertTrue(!interval.startUtc().isBefore(start));
                assertTrue(!interval.endUtc().isAfter(end));
            }));
        }
    }

    private Location location(double latitude, double longitude, String timezone) {
        return new Location(1, "test", "", "", "", latitude, longitude, timezone);
    }
}
