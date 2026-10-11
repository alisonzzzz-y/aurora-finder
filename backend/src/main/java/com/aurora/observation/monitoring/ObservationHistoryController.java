package com.aurora.observation.monitoring;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/history")
public class ObservationHistoryController {
    private final ObservationHistoryService history;
    public ObservationHistoryController(ObservationHistoryService history) {this.history=history;}
    @GetMapping("/{locationId}") public ObservationHistoryService.History history(@PathVariable long locationId) {return history.history(locationId);}
}
