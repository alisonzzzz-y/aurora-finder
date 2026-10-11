package com.aurora.observation.service;

import com.aurora.observation.dto.*;
import java.time.Instant;

public final class HorizonAuroraService {
    private HorizonAuroraService() {}
    private static final String VERSION = "poleward-candidate-v1";
    public static HorizonAurora evaluate(Location location, AuroraMapResponse map, Instant now) {
        String direction = location.latitude() < 0 ? "SOUTH" : "NORTH";
        if (map == null || map.status() != ForecastStatus.CURRENT || map.forecastTime() == null
                || !map.forecastTime().isAfter(now) || map.points() == null)
            return new HorizonAurora("UNAVAILABLE", direction, null, null, null, null, null, null, VERSION);
        AuroraMapPoint candidate = null;
        double nearest = Double.POSITIVE_INFINITY;
        double candidateBearing = 0;
        for (var point : map.points()) {
            if (!Double.isFinite(point.latitude()) || !Double.isFinite(point.longitude())
                    || Math.abs(point.latitude()) > 90 || point.auroraValue() < 18 || point.auroraValue() > 100) continue;
            double lat1 = Math.toRadians(location.latitude()), lat2 = Math.toRadians(point.latitude());
            double delta = Math.toRadians(point.longitude() - location.longitude());
            double hav = Math.pow(Math.sin((lat2-lat1)/2),2) + Math.cos(lat1)*Math.cos(lat2)*Math.pow(Math.sin(delta/2),2);
            double distance = 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, hav))));
            double bearing = (Math.toDegrees(Math.atan2(Math.sin(delta)*Math.cos(lat2),
                    Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(delta)))+360)%360;
            boolean poleward = direction.equals("NORTH") ? bearing <= 60 || bearing >= 300 : bearing >= 120 && bearing <= 240;
            if (!poleward || distance < 100 || distance > 1000 || distance >= nearest) continue;
            nearest = distance; candidate = point; candidateBearing = bearing;
        }
        return candidate == null
                ? new HorizonAurora("NO_CANDIDATE", direction, null, null, null, null, null, map.forecastTime(), VERSION)
                : new HorizonAurora("CANDIDATE", direction, Math.round(nearest*10)/10.0,
                    Math.round(candidateBearing*10)/10.0, candidate.auroraValue(), candidate.latitude(),
                    candidate.longitude(), map.forecastTime(), VERSION);
    }
}
