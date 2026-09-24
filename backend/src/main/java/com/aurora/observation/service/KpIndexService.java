package com.aurora.observation.service;

import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.provider.KpIndexProvider;
import org.springframework.stereotype.Service;

@Service
public class KpIndexService {
    private final KpIndexProvider provider;

    public KpIndexService(KpIndexProvider provider) {
        this.provider = provider;
    }

    public KpIndexResponse latest() {
        return provider.latest();
    }
}
