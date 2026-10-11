package com.aurora.observation.monitoring;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class MonitoringController {
    private final MonitoringService service;
    public MonitoringController(MonitoringService service) { this.service=service; }
    @GetMapping("/solar-wind") public MonitoringService.Solar solar() {return service.solar();}
    @GetMapping("/regional-activity") public MonitoringService.Regional regional() {return service.regional();}
}
