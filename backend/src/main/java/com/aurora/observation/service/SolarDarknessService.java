package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.SolarDarkness;
import com.aurora.observation.dto.SolarDarkness.Status;
import com.aurora.observation.dto.SolarDarkness.Threshold;
import com.aurora.observation.dto.SolarDarkness.ThresholdWindow;
import com.aurora.observation.dto.SolarDarkness.TimeInterval;
import net.e175.klaus.solarpositioning.DeltaT;
import net.e175.klaus.solarpositioning.SPA;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Calculates candidate solar-altitude windows without assigning an aurora-viewing rating. */
@Service
public class SolarDarknessService {
    private static final Duration SAMPLE_STEP = Duration.ofMinutes(5);
    private static final Duration CROSSING_PRECISION = Duration.ofSeconds(30);

    public SolarDarkness forWindow(Location location, Instant startUtc, Instant endUtc) {
        List<ThresholdWindow> windows = new ArrayList<>();
        for (Threshold threshold : Threshold.values()) {
            try {
                List<TimeInterval> intervals = findIntervals(location, startUtc, endUtc,
                        threshold.solarElevationDegrees());
                Status status = intervals.isEmpty() ? Status.NO_INTERVAL
                        : intervals.size() == 1
                        && intervals.getFirst().startUtc().equals(startUtc)
                        && intervals.getFirst().endUtc().equals(endUtc)
                        ? Status.CONTINUOUS : Status.INTERVALS_FOUND;
                windows.add(new ThresholdWindow(threshold, threshold.solarElevationDegrees(), status,
                        List.copyOf(intervals)));
            } catch (RuntimeException exception) {
                windows.add(new ThresholdWindow(threshold, threshold.solarElevationDegrees(),
                        Status.CALCULATION_FAILED, List.of()));
            }
        }
        return new SolarDarkness(List.copyOf(windows));
    }

    private List<TimeInterval> findIntervals(Location location, Instant start, Instant end,
                                             double thresholdDegrees) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Solar evaluation window must have positive duration");
        }

        List<TimeInterval> intervals = new ArrayList<>();
        Instant intervalStart = isBelow(location, start, thresholdDegrees) ? start : null;
        Instant previous = start;
        boolean previousBelow = intervalStart != null;

        while (previous.isBefore(end)) {
            Instant next = previous.plus(SAMPLE_STEP);
            if (next.isAfter(end)) next = end;
            boolean nextBelow = isBelow(location, next, thresholdDegrees);
            if (previousBelow != nextBelow) {
                Instant crossing = findCrossing(location, previous, next, thresholdDegrees, previousBelow);
                if (nextBelow) {
                    intervalStart = crossing;
                } else {
                    intervals.add(new TimeInterval(intervalStart, crossing));
                    intervalStart = null;
                }
            }
            previous = next;
            previousBelow = nextBelow;
        }

        if (intervalStart != null) intervals.add(new TimeInterval(intervalStart, end));
        return intervals;
    }

    private Instant findCrossing(Location location, Instant left, Instant right,
                                 double thresholdDegrees, boolean leftBelow) {
        while (Duration.between(left, right).compareTo(CROSSING_PRECISION) > 0) {
            Instant middle = left.plusMillis(Duration.between(left, right).toMillis() / 2);
            if (isBelow(location, middle, thresholdDegrees) == leftBelow) left = middle;
            else right = middle;
        }
        return left.plusMillis(Duration.between(left, right).toMillis() / 2);
    }

    private boolean isBelow(Location location, Instant instant, double thresholdDegrees) {
        ZonedDateTime utcTime = instant.atZone(ZoneOffset.UTC);
        double deltaT = DeltaT.estimate(LocalDate.from(utcTime));
        double zenith = SPA.calculateSolarPosition(utcTime, location.latitude(), location.longitude(),
                0, deltaT).zenithAngle();
        double elevation = 90.0 - zenith;
        if (!Double.isFinite(elevation)) throw new IllegalStateException("Solar elevation is not finite");
        return elevation <= thresholdDegrees;
    }
}
