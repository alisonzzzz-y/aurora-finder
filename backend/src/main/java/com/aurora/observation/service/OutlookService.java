package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.NightOutlook;
import com.aurora.observation.dto.OutlookResponse;
import com.aurora.observation.dto.OutlookLevel;
import com.aurora.observation.dto.OutlookReasonCode;
import com.aurora.observation.dto.RuleStatus;
import com.aurora.observation.dto.SolarDarkness;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class OutlookService {
    private final LocationService locations;
    private final Clock clock;
    private final SolarDarknessService solarDarkness;

    public OutlookService(LocationService locations, Clock clock, SolarDarknessService solarDarkness) {
        this.locations = locations;
        this.clock = clock;
        this.solarDarkness = solarDarkness;
    }

    public OutlookResponse forLocation(long id) {
        Location location = locations.get(id);
        ZoneId zone = ZoneId.of(location.timezone());
        LocalDate tonight = currentNightDate(location, zone, clock.instant());
        List<NightOutlook> nights = IntStream.range(0, 3)
                .mapToObj(offset -> {
                    LocalDate localDate = tonight.plusDays(offset);
                    Instant windowStart = localDate.atTime(LocalTime.NOON).atZone(zone).toInstant();
                    ZoneOffset utcOffset = zone.getRules().getOffset(windowStart);
                    String offsetId = utcOffset.equals(ZoneOffset.UTC) ? "+00:00" : utcOffset.getId();
                    Instant windowEnd = localDate.plusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant();
                    return new NightOutlook(localDate, offsetId, windowStart, windowEnd,
                            OutlookLevel.INSUFFICIENT_DATA,
                            OutlookReasonCode.RULES_NOT_VALIDATED,
                            solarDarkness.forWindow(location, windowStart, windowEnd));
                })
                .toList();
        return new OutlookResponse(location, clock.instant(), RuleStatus.NOT_VALIDATED, nights);
    }

    /**
     * A night keeps the date of the evening it began until civil dawn, so the hours after midnight
     * still belong to "tonight". Without civil darkness (polar day) the calendar date is used.
     */
    LocalDate currentNightDate(Location location, ZoneId zone, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        Instant todayNoon = today.atTime(LocalTime.NOON).atZone(zone).toInstant();
        if (!now.isBefore(todayNoon)) return today;
        LocalDate yesterday = today.minusDays(1);
        Instant yesterdayNoon = yesterday.atTime(LocalTime.NOON).atZone(zone).toInstant();
        var civil = solarDarkness.forWindow(location, yesterdayNoon, todayNoon).thresholds().stream()
                .filter(threshold -> threshold.threshold() == SolarDarkness.Threshold.CIVIL_TWILIGHT)
                .findFirst().orElse(null);
        if (civil == null || civil.status() == SolarDarkness.Status.CALCULATION_FAILED) {
            // Without a solar result, treat the small hours as the previous evening's night.
            return now.atZone(zone).getHour() < 6 ? yesterday : today;
        }
        boolean nightInProgress = civil.intervals().stream().anyMatch(interval -> now.isBefore(interval.endUtc()));
        return nightInProgress ? yesterday : today;
    }
}
