package com.aurora.observation.provider;

import com.aurora.observation.dto.KpIndexResponse;

public interface KpIndexProvider {
    KpIndexResponse latest();
}
