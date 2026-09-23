package com.aurora.observation.service;

import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.NightOutlook;
import com.aurora.observation.dto.OutlookResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class OutlookService {
    private final LocationService locations;
    private final Clock clock;

    public OutlookService(LocationService locations, Clock clock) {
        this.locations = locations;
        this.clock = clock;
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
                    return new NightOutlook(localDate, offsetId, "INSUFFICIENT_DATA",
                            "Aurora, cloud, darkness, and freshness rules are pending validation.");
                })
                .toList();
        return new OutlookResponse(location, clock.instant(), "NOT_VALIDATED", nights);
    }
}
