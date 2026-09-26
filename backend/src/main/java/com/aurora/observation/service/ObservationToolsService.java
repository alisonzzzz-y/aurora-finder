package com.aurora.observation.service;

import com.aurora.observation.dto.GeomagneticStormForecastResponse;
import com.aurora.observation.dto.GeomagneticWarningsResponse;
import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.ObservationFactsResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/** Read-only, source-backed operations available to a future assistant adapter. */
@Service
public class ObservationToolsService {
    private final LocationService locations;
    private final ObservationFactsService facts;
    private final KpIndexService kpIndex;
    private final GeomagneticStormForecastService stormForecast;
    private final GeomagneticWarningsService warnings;

    public ObservationToolsService(LocationService locations, ObservationFactsService facts,
                                   KpIndexService kpIndex, GeomagneticStormForecastService stormForecast,
                                   GeomagneticWarningsService warnings) {
        this.locations = locations;
        this.facts = facts;
        this.kpIndex = kpIndex;
        this.stormForecast = stormForecast;
        this.warnings = warnings;
    }

    /** Search candidates first; downstream tools use a selected provider location ID. */
    public List<Location> searchPlaces(String query) {
        return locations.search(query);
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