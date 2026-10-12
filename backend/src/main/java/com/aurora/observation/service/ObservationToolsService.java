package com.aurora.observation.service;

import com.aurora.observation.dto.GeomagneticStormForecastResponse;
import com.aurora.observation.dto.GeomagneticWarningsResponse;
import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.NightOutlook;
import com.aurora.observation.dto.ObservationFactsResponse;
import com.aurora.observation.dto.OutlookResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Read-only, source-backed operations available to a future assistant adapter. */
@Service
public class ObservationToolsService {
    private final com.aurora.observation.monitoring.ObservationHistoryService history;
    private final java.time.Clock clock;
    private final LocationService locations;
    private final ObservationFactsService facts;
    private final OutlookService outlooks;
    private final KpIndexService kpIndex;
    private final GeomagneticStormForecastService stormForecast;
    private final GeomagneticWarningsService warnings;

    public ObservationToolsService(LocationService locations, ObservationFactsService facts, OutlookService outlooks,
                                   KpIndexService kpIndex, GeomagneticStormForecastService stormForecast,
                                   GeomagneticWarningsService warnings, com.aurora.observation.monitoring.ObservationHistoryService history, java.time.Clock clock) {
        this.history = history;
        this.clock = clock;
        this.locations = locations;
        this.facts = facts;
        this.outlooks = outlooks;
        this.kpIndex = kpIndex;
        this.stormForecast = stormForecast;
        this.warnings = warnings;
    }

    /** Search candidates first; downstream tools use a selected provider location ID. */
    public List<Location> searchPlaces(String query) {
        return locations.search(query);
    }

    /** Selects one explicitly requested local date from the supported three-night window. */
    public NightOutlook getNightOutlook(long locationId, LocalDate localDate) {
        Objects.requireNonNull(localDate, "localDate");
        OutlookResponse outlook = outlooks.forLocation(locationId);
        return outlook.nights().stream()
                .filter(night -> night.localDate().equals(localDate))
                .findFirst()
                .orElseThrow(() -> new UnsupportedNightDateException(localDate,
                        outlook.nights().stream().map(NightOutlook::localDate).toList()));
    }

    /** Returns existing local facts, including their source status and time coverage. */
    public ObservationFactsResponse getLocalNightFacts(long locationId) {
        return facts.forLocation(locationId);
    }

    public KpIndexResponse getGlobalKpForecast() {
        return kpIndex.latest();
    }

    public record RecentGeomagneticHistory(java.time.Instant queriedAtUtc, java.time.Instant retrievedAt,
            String source, String scope, List<com.aurora.observation.dto.KpIndexRecord> records) {}

    public RecentGeomagneticHistory getRecentGeomagneticHistory() {
        var now = clock.instant();
        var data = kpIndex.latest();
        var records = data.records().stream().filter(r ->
                r.type() != com.aurora.observation.dto.KpIndexType.PREDICTED
                && !r.periodStart().isBefore(now.minus(java.time.Duration.ofDays(3)))
                && !r.periodStart().plusSeconds(10800).isAfter(now))
                .sorted(java.util.Comparator.comparing(com.aurora.observation.dto.KpIndexRecord::periodStart)).toList();
        return new RecentGeomagneticHistory(now, data.retrievedAt(), data.source(),
                "Available completed global three-hour readings from the past 72 hours. Gaps are unknown; not local sighting evidence.", records);
    }

    public record LocalHistory(String status, LocalDate requestedLocalDate, String scope,
            List<com.aurora.observation.monitoring.ObservationSnapshot> snapshots) {}

    public LocalHistory getLocalHistory(long locationId, LocalDate localDate) {
        var result = history.history(locationId);
        var selected = result.snapshots().stream().filter(snapshot -> {
            var zone = java.time.ZoneId.of(snapshot.timezone());
            var start = localDate.atTime(12, 0).atZone(zone).toInstant();
            var end = localDate.plusDays(1).atTime(12, 0).atZone(zone).toInstant();
            return !snapshot.capturedAt().isBefore(start) && snapshot.capturedAt().isBefore(end);
        }).toList();
        return new LocalHistory(result.status(), localDate,
                "Saved forecast snapshots captured between local noon and next noon, retained for seven days. Not verified sightings; empty means no stored evidence, not no aurora.", selected);
    }

    public GeomagneticStormForecastResponse getThreeDayStormForecast() {
        return stormForecast.latest();
    }

    public GeomagneticWarningsResponse getActiveGeomagneticWarnings() {
        return warnings.latest();
    }
}