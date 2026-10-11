package com.aurora.observation.service;

import com.aurora.observation.dto.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HorizonAuroraServiceTest {
    private final Instant now = Instant.parse("2026-10-11T00:00:00Z");
    private Location place(double latitude, double longitude) {
        return new Location(1, "Test", "", "", "Test", latitude, longitude, "UTC");
    }
    private AuroraMapResponse map(AuroraMapPoint... points) {
        return new AuroraMapResponse(ForecastStatus.CURRENT, now.minusSeconds(600), now.plusSeconds(3600), now, "NOAA", List.of(points));
    }
    @Test void findsNorthwardCandidateDespiteZeroLocalReading() {
        var result = HorizonAuroraService.evaluate(place(53,-6), map(new AuroraMapPoint(-6,53,0), new AuroraMapPoint(-6,58,25)), now);
        assertEquals("CANDIDATE", result.status());
        assertEquals("NORTH", result.direction());
        assertEquals(556, result.distanceKm(), 1);
        assertEquals(25, result.modelSignal());
    }
    @Test void rejectsTooFarWrongDirectionAndWeakCells() {
        var result = HorizonAuroraService.evaluate(place(53,-6), map(new AuroraMapPoint(-6,65,70), new AuroraMapPoint(-6,48,70), new AuroraMapPoint(-6,58,17)), now);
        assertEquals("NO_CANDIDATE", result.status());
        assertNull(result.distanceKm());
    }
    @Test void southernHemisphereLooksSouthAndChoosesNearestCandidate() {
        var result = HorizonAuroraService.evaluate(place(-45,170), map(new AuroraMapPoint(170,-51,80), new AuroraMapPoint(170,-48,18), new AuroraMapPoint(170,-40,90)), now);
        assertEquals("SOUTH", result.direction());
        assertEquals(-48, result.latitude());
        assertEquals(180, result.bearingDegrees(), 0.1);
    }
    @Test void emptyCurrentActivityIsNotAnOutage() {
        assertEquals("NO_CANDIDATE", HorizonAuroraService.evaluate(place(53,0), map(), now).status());
    }
    @Test void handlesLongitudeWrapAndExpiredData() {
        assertEquals("CANDIDATE", HorizonAuroraService.evaluate(place(53,179), map(new AuroraMapPoint(-179,58,40)), now).status());
        assertEquals("UNAVAILABLE", HorizonAuroraService.evaluate(place(53,0), map(new AuroraMapPoint(0,58,40)), now.plusSeconds(3600)).status());
        assertEquals("UNAVAILABLE", HorizonAuroraService.evaluate(place(53,0), null, now).status());
    }
}
