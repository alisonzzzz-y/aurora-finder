package com.aurora.observation.provider;

import com.aurora.observation.dto.Location;
import java.util.List;
import java.util.Optional;

public interface GeocodingProvider {
    List<Location> search(String query);
    List<Location> search(String query, String language);
    Optional<Location> get(long id);
    Optional<Location> get(long id, String language);
}
