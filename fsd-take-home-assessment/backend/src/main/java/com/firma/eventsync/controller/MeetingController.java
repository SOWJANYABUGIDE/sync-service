package com.firma.eventsync.controller;

import com.firma.eventsync.model.ReconciliationStats;
import com.firma.eventsync.model.UnifiedMeeting;
import com.firma.eventsync.service.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MeetingController {

    private final ReconciliationService reconciliationService;

    public MeetingController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/meetings")
    public List<UnifiedMeeting> getMeetings(
            @RequestParam(name = "conflictsOnly", defaultValue = "false") boolean conflictsOnly) {
        List<UnifiedMeeting> all = reconciliationService.getMeetings();
        if (!conflictsOnly) {
            return all;
        }
        return all.stream().filter(UnifiedMeeting::hasConflicts).toList();
    }

    @GetMapping("/meetings/{id}")
    public ResponseEntity<UnifiedMeeting> getMeeting(@PathVariable String id) {
        return reconciliationService.getMeeting(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ReconciliationStats getStats() {
        return reconciliationService.getStats();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
