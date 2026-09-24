package com.aurora.observation.dto;

import java.time.Instant;
import java.util.List;

public record KpIndexResponse(Instant retrievedAt, String source, List<KpIndexRecord> records) {}
