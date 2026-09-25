package com.aurora.observation.provider;

import com.aurora.observation.dto.GeomagneticStormForecastResponse;

public interface GeomagneticStormForecastProvider {
    GeomagneticStormForecastResponse latest();
}
