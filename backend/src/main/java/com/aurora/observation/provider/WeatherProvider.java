package com.aurora.observation.provider;

import com.aurora.observation.dto.WeatherForecastResponse;

public interface WeatherProvider {
    WeatherForecastResponse forecast(double latitude, double longitude);
}
