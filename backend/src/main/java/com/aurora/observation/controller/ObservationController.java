package com.aurora.observation.controller;

import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.OutlookResponse;
import com.aurora.observation.dto.AuroraMapResponse;
import com.aurora.observation.service.LocationService;
import com.aurora.observation.service.OutlookService;
import com.aurora.observation.service.AuroraMapService;
import com.aurora.observation.service.KpIndexService;
import com.aurora.observation.dto.KpIndexResponse;
import com.aurora.observation.dto.LocalAuroraActivityResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ObservationController {
    private final LocationService locations;
    private final OutlookService outlook;
    private final AuroraMapService auroraMap;
    private final KpIndexService kpIndex;

    public ObservationController(LocationService locations, OutlookService outlook, AuroraMapService auroraMap,
                                 KpIndexService kpIndex) {
        this.locations = locations;
        this.outlook = outlook;
        this.auroraMap = auroraMap;
        this.kpIndex = kpIndex;
    }

    @GetMapping("/locations")
    public List<Location> search(@RequestParam String q) {
        return locations.search(q);
    }

    @GetMapping("/outlooks/{locationId}")
    public OutlookResponse outlook(@PathVariable long locationId) {
        return outlook.forLocation(locationId);
    }

    @GetMapping("/aurora-map")
    public AuroraMapResponse auroraMap() {
        return auroraMap.latest();
    }

    @GetMapping("/aurora-activity")
    public LocalAuroraActivityResponse auroraActivity(@RequestParam double latitude, @RequestParam double longitude) {
        return auroraMap.forCoordinates(latitude, longitude);
    }

    @GetMapping("/kp-index")
    public KpIndexResponse kpIndex() {
        return kpIndex.latest();
    }
}
