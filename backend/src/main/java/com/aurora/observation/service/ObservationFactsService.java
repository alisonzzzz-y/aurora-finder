package com.aurora.observation.service;

import com.aurora.observation.dto.FactFetchStatus;
import com.aurora.observation.dto.FactTimeScope;
import com.aurora.observation.dto.ForecastCoverage;
import com.aurora.observation.dto.LocalAuroraActivityResponse;
import com.aurora.observation.dto.ObservationFactsResponse;
import com.aurora.observation.dto.OutlookResponse;
import com.aurora.observation.dto.SourceFact;
import com.aurora.observation.dto.SolarDarkness;
import com.aurora.observation.dto.SolarNightFact;
import com.aurora.observation.dto.WeatherForecastResponse;
import com.aurora.observation.provider.ProviderFailure;
import com.aurora.observation.provider.ProviderUnavailableException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class ObservationFactsService {
    private static final String NOAA_URL = "https://www.spaceweather.gov/products/aurora-30-minute-forecast";
    private static final String MET_NO_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact";
    private static final String SPA_URL = "https://github.com/KlausBrunner/solarpositioning";

    private final OutlookService outlooks;
    private final AuroraMapService aurora;
    private final WeatherService weather;
    private final Clock clock;

    public ObservationFactsService(OutlookService outlooks, AuroraMapService aurora,
                                   WeatherService weather, Clock clock) {
        this.outlooks = outlooks;
        this.aurora = aurora;
        this.weather = weather;
        this.clock = clock;
    }

    public ObservationFactsResponse forLocation(long locationId) {
        OutlookResponse outlook = outlooks.forLocation(locationId);
        var location = outlook.location();
        SourceFact<LocalAuroraActivityResponse> auroraFact = loadAurora(location.latitude(), location.longitude());
        SourceFact<WeatherForecastResponse> cloudFact = loadClouds(location.latitude(), location.longitude(),
                location.timezone());
        SourceFact<List<SolarNightFact>> solarFact = solarFacts(outlook);
        List<SourceFact<?>> providerFacts = List.of(auroraFact, cloudFact);
        long usableSources = providerFacts.stream()
                .filter(fact -> fact.status() == FactFetchStatus.CURRENT).count();
        FactFetchStatus aggregate = usableSources == providerFacts.size() ? FactFetchStatus.CURRENT
                : usableSources == 0 ? FactFetchStatus.UNAVAILABLE : FactFetchStatus.PARTIAL;
        return new ObservationFactsResponse(clock.instant(), outlook, auroraFact, cloudFact, solarFact,
                compareCoverage(auroraFact, cloudFact), aggregate);
    }

    private SourceFact<LocalAuroraActivityResponse> loadAurora(double latitude, double longitude) {
        try {
            LocalAuroraActivityResponse response = aurora.forCoordinates(latitude, longitude);
            FactFetchStatus status = response.status() == com.aurora.observation.dto.ForecastStatus.EXPIRED
                    ? FactFetchStatus.EXPIRED : FactFetchStatus.CURRENT;
            return new SourceFact<>(status, FactTimeScope.SHORT_RANGE, response.retrievedAt(), response.observationTime(), response.forecastTime(),
                    response.observationTime(), response.forecastTime(), response.source(), NOAA_URL, null, response);
        } catch (ProviderUnavailableException error) {
            return unavailable("NOAA OVATION", NOAA_URL, error.failure());
        }
    }

    private SourceFact<List<SolarNightFact>> solarFacts(OutlookResponse outlook) {
        List<SolarNightFact> nights = outlook.nights().stream()
                .map(night -> new SolarNightFact(night.localDate(), night.solarDarkness())).toList();
        long failures = nights.stream().flatMap(night -> night.solarDarkness().thresholds().stream())
                .filter(threshold -> threshold.status() == SolarDarkness.Status.CALCULATION_FAILED).count();
        FactFetchStatus status = failures == 0 ? FactFetchStatus.CURRENT
                : failures == (long) nights.size() * SolarDarkness.Threshold.values().length
                ? FactFetchStatus.UNAVAILABLE : FactFetchStatus.PARTIAL;
        Instant scopeStart = outlook.nights().stream().map(night -> night.evaluationWindowStartUtc())
                .min(Comparator.naturalOrder()).orElse(null);
        Instant scopeEnd = outlook.nights().stream().map(night -> night.evaluationWindowEndUtc())
                .max(Comparator.naturalOrder()).orElse(null);
        return new SourceFact<>(status, FactTimeScope.THREE_LOCAL_NIGHTS, outlook.generatedAtUtc(), null, null, scopeStart, scopeEnd,
                "Solar Position Algorithm (SPA), solarpositioning 2.0.13", SPA_URL, null, nights);
    }

    private SourceFact<WeatherForecastResponse> loadClouds(double latitude, double longitude, String timezone) {
        try {
            WeatherForecastResponse response = weather.forecast(latitude, longitude, timezone);
            List<Instant> times = response.cloudForecast().stream().map(point -> point.validAt()).sorted().toList();
            FactFetchStatus status = response.cloudForecast().stream().noneMatch(point -> point.cloudCoverPercent() != null)
                    ? FactFetchStatus.NO_COVERAGE : FactFetchStatus.CURRENT;
            return new SourceFact<>(status, FactTimeScope.TONIGHT, response.retrievedAt(), null, null,
                    times.stream().min(Comparator.naturalOrder()).orElse(null),
                    times.stream().max(Comparator.naturalOrder()).orElse(null), response.source(), MET_NO_URL,
                    null, response);
        } catch (ProviderUnavailableException error) {
            return unavailable("MET Norway Locationforecast", MET_NO_URL, error.failure());
        }
    }

    private <T> SourceFact<T> unavailable(String source, String sourceUrl, ProviderFailure failure) {
        FactTimeScope scope = source.equals("NOAA OVATION") ? FactTimeScope.SHORT_RANGE : FactTimeScope.TONIGHT;
        return new SourceFact<>(FactFetchStatus.UNAVAILABLE, scope, null, null, null, null, null,
                source, sourceUrl, failure, null);
    }

    private ForecastCoverage compareCoverage(SourceFact<LocalAuroraActivityResponse> auroraFact,
                                             SourceFact<WeatherForecastResponse> cloudFact) {
        Instant rangeStart = auroraFact.scopeStartUtc();
        Instant rangeEnd = auroraFact.scopeEndUtc();
        if (auroraFact.status() != FactFetchStatus.CURRENT || cloudFact.status() != FactFetchStatus.CURRENT
                || auroraFact.data() == null || cloudFact.data() == null || rangeStart == null || rangeEnd == null) {
            return new ForecastCoverage(ForecastCoverage.Status.CANNOT_CHECK, auroraFact.timeScope(),
                    cloudFact.timeScope(), rangeStart, rangeEnd, 0);
        }
        int points = (int) cloudFact.data().cloudForecast().stream()
                .filter(point -> point.cloudCoverPercent() != null)
                .filter(point -> !point.validAt().isBefore(rangeStart) && !point.validAt().isAfter(rangeEnd))
                .count();
        return new ForecastCoverage(points > 0 ? ForecastCoverage.Status.OVERLAPS
                : ForecastCoverage.Status.NO_OVERLAP, auroraFact.timeScope(), cloudFact.timeScope(),
                rangeStart, rangeEnd, points);
    }
}
