package com.company.meetinghelper.participant;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meetings/{meetingId}/participants")
public class ParticipantController {
    private final ParticipantService participantService;

    public ParticipantController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @PostMapping
    ParticipantService.ParticipantResult create(
            @PathVariable String meetingId,
            @Valid @RequestBody ParticipantService.CreateParticipantRequest request
    ) {
        return participantService.create(meetingId, request);
    }

    @DeleteMapping("/{participantId}")
    ResponseEntity<Void> delete(@PathVariable String meetingId, @PathVariable String participantId) {
        participantService.delete(meetingId, participantId);
        return ResponseEntity.noContent().build();
    }
}

