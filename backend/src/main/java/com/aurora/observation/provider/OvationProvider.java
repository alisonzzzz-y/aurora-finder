package com.aurora.observation.provider;

import com.aurora.observation.dto.AuroraMapResponse;

public interface OvationProvider {
    AuroraMapResponse latest();
}
