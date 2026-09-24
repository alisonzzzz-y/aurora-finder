package com.aurora.observation.provider;

import com.aurora.observation.dto.OvationForecast;

public interface OvationProvider {
    OvationForecast latest();
}
