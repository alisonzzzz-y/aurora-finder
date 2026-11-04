package com.aurora.observation.controller;

import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.OutlookResponse;
import com.aurora.observation.dto.AuroraMapResponse;
import com.aurora.observation.service.LocationService;
import com.aurora.observation.service.OutlookService;
import com.aurora.observation.service.AuroraMapService;
import com.aurora.observation.service.KpIndexService;
import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.dto.LocalAuroraActivityResponse;
import com.aurora.observation.dto.WeatherForecastResponse;
import com.aurora.observation.dto.ObservationFactsResponse;
import com.aurora.observation.dto.GeomagneticStormForecastResponse;
import com.aurora.observation.dto.GeomagneticWarningsResponse;
import com.aurora.observation.service.ObservationFactsService;
import com.aurora.observation.service.GeomagneticStormForecastService;
import com.aurora.observation.service.GeomagneticWarningsService;
import com.aurora.observation.service.WeatherService;
import com.aurora.observation.record.RunRecordStore;
import com.aurora.observation.record.RunRecordUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ObservationController {
    private static final Logger log = LoggerFactory.getLogger(ObservationController.class);
    private final LocationService locations;
    private final OutlookService outlook;
    private final AuroraMapService auroraMap;
    private final KpIndexService kpIndex;
    private final WeatherService weather;
    private final ObservationFactsService facts;
    private final GeomagneticStormForecastService geomagneticStormForecast;
    private final GeomagneticWarningsService geomagneticWarnings;
    private final RunRecordStore records;
    private final com.aurora.observation.monitoring.ObservationHistoryService history;

    public ObservationController(LocationService locations, OutlookService outlook, AuroraMapService auroraMap,
                                 KpIndexService kpIndex, WeatherService weather, ObservationFactsService facts,
                                 GeomagneticStormForecastService geomagneticStormForecast,
                                 GeomagneticWarningsService geomagneticWarnings, RunRecordStore records,
                                 com.aurora.observation.monitoring.ObservationHistoryService history) {
        this.locations = locations;
        this.outlook = outlook;
        this.auroraMap = auroraMap;
        this.kpIndex = kpIndex;
        this.weather = weather;
        this.facts = facts;
        this.geomagneticStormForecast = geomagneticStormForecast;
        this.geomagneticWarnings = geomagneticWarnings;
        this.records = records;
        this.history = history;
    }

    @GetMapping("/locations")
    public List<Location> search(@RequestParam String q) {
        return locations.search(q);
    }

    @GetMapping("/outlooks/{locationId}")
    public OutlookResponse outlook(@PathVariable long locationId) {
        return outlook.forLocation(locationId);
    }

    @GetMapping("/facts/{locationId}")
    public ObservationFactsResponse facts(@PathVariable long locationId) {
        // Run records are an audit trail. A database outage must not hide the place's source facts.
        String runId = beginRecord(locationId);
        ObservationFactsResponse result;
        try {
            result = facts.forLocation(locationId);
        } catch (RuntimeException failure) {
            finishRecord(runId, "FAILED");
            throw failure;
        }
        if (runId != null) {
            try {
                records.recordFacts(runId, result);
                records.finish(runId, "COMPLETED");
            } catch (RunRecordUnavailableException failure) {
                log.error("Page facts were served without a complete run record.", failure);
                finishRecord(runId, "FAILED");
                runId = null;
            }
        }
        history.capture(result);
        return new ObservationFactsResponse(result.generatedAtUtc(), result.outlook(), result.auroraActivity(),
                result.cloudForecast(), result.solarDarkness(), result.coverage(), result.sourceStatus(), runId, result.viewingConditions());
    }

    private String beginRecord(long locationId) {
        try {
            return records.begin("PAGE_FACTS", locationId);
        } catch (RunRecordUnavailableException failure) {
            log.error("Page facts are being served without a run record.", failure);
            return null;
        }
    }

    private void finishRecord(String runId, String status) {
        if (runId == null) return;
        try {
            records.finish(runId, status);
        } catch (RunRecordUnavailableException failure) {
            log.warn("Run record {} could not be marked {}.", runId, status);
        }
    }

    @GetMapping("/aurora-map")
    public AuroraMapResponse auroraMap() {
        return auroraMap.latest();
    }

    @GetMapping("/aurora-activity")
    public LocalAuroraActivityResponse auroraActivity(@RequestParam double latitude, @RequestParam double longitude) {
        return auroraMap.forCoordinates(latitude, longitude);
    }

    @GetMapping("/kp-index")
    public KpIndexResponse kpIndex() {
        return kpIndex.latest();
    }

    @GetMapping("/geomagnetic-storm-forecast")
    public GeomagneticStormForecastResponse geomagneticStormForecast() {
        return geomagneticStormForecast.latest();
    }

    @GetMapping("/geomagnetic-warnings")
    public GeomagneticWarningsResponse geomagneticWarnings() {
        return geomagneticWarnings.latest();
    }

    @GetMapping("/weather-forecast")
    public WeatherForecastResponse weatherForecast(@RequestParam double latitude, @RequestParam double longitude,
                                                   @RequestParam String timezone) {
        return weather.forecast(latitude, longitude, timezone);
    }
}
