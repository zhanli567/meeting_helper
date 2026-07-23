package com.company.meetinghelper.seating;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
public class SeatingController {
    private final SeatingService seatingService;
    private final PlanVersionService versionService;

    public SeatingController(SeatingService seatingService, PlanVersionService versionService) {
        this.seatingService = seatingService;
        this.versionService = versionService;
    }

    @PostMapping("/{planId}/assignments")
    ResponseEntity<Void> assign(
            @PathVariable String planId,
            @Valid @RequestBody SeatingService.AssignmentRequest request
    ) {
        seatingService.assign(planId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{planId}/participants/{participantId}/assignment")
    ResponseEntity<Void> unassign(@PathVariable String planId, @PathVariable String participantId) {
        seatingService.unassign(planId, participantId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{planId}/participants/{participantId}/lock")
    ResponseEntity<Void> lock(
            @PathVariable String planId,
            @PathVariable String participantId,
            @RequestParam boolean locked
    ) {
        seatingService.setLocked(planId, participantId, locked);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{planId}/versions")
    PlanVersionService.VersionResult version(
            @PathVariable String planId,
            @Valid @RequestBody PlanVersionService.CreateVersionRequest request
    ) {
        return versionService.create(planId, request);
    }
}

