package com.company.meetinghelper.seating.api;

import com.company.meetinghelper.seating.api.dto.request.AssignmentRequest;
import com.company.meetinghelper.seating.api.dto.request.CreateVersionRequest;
import com.company.meetinghelper.seating.api.dto.response.RestoreVersionResult;
import com.company.meetinghelper.seating.api.dto.response.VersionResult;
import com.company.meetinghelper.seating.service.PlanVersionService;
import com.company.meetinghelper.seating.service.SeatingService;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
            @Valid @RequestBody AssignmentRequest request
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
    VersionResult version(
            @PathVariable String planId,
            @Valid @RequestBody CreateVersionRequest request
    ) {
        return versionService.create(planId, request);
    }

    @GetMapping("/{planId}/versions/{versionId}")
    WorkspaceResponse versionSnapshot(
            @PathVariable String planId,
            @PathVariable String versionId
    ) {
        return versionService.getSnapshot(planId, versionId);
    }

    @PostMapping("/{planId}/versions/{versionId}/restore")
    RestoreVersionResult restoreVersion(
            @PathVariable String planId,
            @PathVariable String versionId
    ) {
        return versionService.restore(planId, versionId);
    }
}
