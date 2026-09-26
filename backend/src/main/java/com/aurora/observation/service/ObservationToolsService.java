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
    private final LocationService locations;
    private final ObservationFactsService facts;
    private final OutlookService outlooks;
    private final KpIndexService kpIndex;
    private final GeomagneticStormForecastService stormForecast;
    private final GeomagneticWarningsService warnings;

    public ObservationToolsService(LocationService locations, ObservationFactsService facts, OutlookService outlooks,
                                   KpIndexService kpIndex, GeomagneticStormForecastService stormForecast,
                                   GeomagneticWarningsService warnings) {
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

    public GeomagneticStormForecastResponse getThreeDayStormForecast() {
        return stormForecast.latest();
    }

    public GeomagneticWarningsResponse getActiveGeomagneticWarnings() {
        return warnings.latest();
    }
}