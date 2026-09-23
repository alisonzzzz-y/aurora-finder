package com.aurora.observation.controller;

import com.aurora.observation.dto.Location;
import com.aurora.observation.dto.OutlookResponse;
import com.aurora.observation.service.LocationService;
import com.aurora.observation.service.OutlookService;
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

    public ObservationController(LocationService locations, OutlookService outlook) {
        this.locations = locations;
        this.outlook = outlook;
    }

    @GetMapping("/locations")
    public List<Location> search(@RequestParam String q) {
        return locations.search(q);
    }

    @GetMapping("/outlooks/{locationId}")
    public OutlookResponse outlook(@PathVariable long locationId) {
        return outlook.forLocation(locationId);
    }
}
