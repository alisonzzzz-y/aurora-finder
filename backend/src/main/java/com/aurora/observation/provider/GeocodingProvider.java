package com.aurora.observation.provider;

import com.aurora.observation.dto.Location;
import java.util.List;
import java.util.Optional;

public interface GeocodingProvider {
    List<Location> search(String query);
    Optional<Location> get(long id);
}
