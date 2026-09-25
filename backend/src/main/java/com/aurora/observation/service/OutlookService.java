package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.NightOutlook;
import com.aurora.observation.dto.OutlookResponse;
import com.aurora.observation.dto.OutlookLevel;
import com.aurora.observation.dto.OutlookReasonCode;
import com.aurora.observation.dto.RuleStatus;
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
        LocalDate tonight = LocalDate.now(clock.withZone(zone));
        List<NightOutlook> nights = IntStream.range(0, 3)
                .mapToObj(offset -> {
                    LocalDate localDate = tonight.plusDays(offset);
                    ZoneOffset utcOffset = zone.getRules().getOffset(localDate.atStartOfDay(zone).toInstant());
                    String offsetId = utcOffset.equals(ZoneOffset.UTC) ? "+00:00" : utcOffset.getId();
                    Instant windowStart = localDate.atTime(LocalTime.NOON).atZone(zone).toInstant();
                    Instant windowEnd = localDate.plusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant();
                    return new NightOutlook(localDate, offsetId, windowStart, windowEnd,
                            OutlookLevel.INSUFFICIENT_DATA,
                            OutlookReasonCode.RULES_NOT_VALIDATED,
                            solarDarkness.forWindow(location, windowStart, windowEnd));
                })
                .toList();
        return new OutlookResponse(location, clock.instant(), RuleStatus.NOT_VALIDATED, nights);
    }
}
