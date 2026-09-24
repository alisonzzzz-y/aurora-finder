package com.aurora.observation.service;

import com.aurora.observation.dto.WeatherForecastResponse;
import com.aurora.observation.dto.WeatherCloudPoint;
import com.aurora.observation.provider.WeatherProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class WeatherService {
    private final WeatherProvider provider;
    private final Clock clock;

    public WeatherService(WeatherProvider provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    public WeatherForecastResponse forecast(double latitude, double longitude, String timezone) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new InvalidWeatherRequestException("Latitude and longitude are outside their valid ranges.");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException error) {
            throw new InvalidWeatherRequestException("A valid IANA time zone is required.");
        }
        WeatherForecastResponse source = provider.forecast(latitude, longitude);
        LocalDate tonight = LocalDate.now(clock.withZone(zone));
        var windowStart = tonight.atTime(LocalTime.NOON).atZone(zone).toInstant();
        var windowEnd = tonight.plusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant();
        List<WeatherCloudPoint> tonightCloud = source.cloudForecast().stream()
                .filter(point -> !point.validAt().isBefore(windowStart) && point.validAt().isBefore(windowEnd))
                .toList();
        return new WeatherForecastResponse(source.retrievedAt(), source.expiresAt(), source.source(),
                source.requestedLatitude(), source.requestedLongitude(), tonightCloud);
    }
}
