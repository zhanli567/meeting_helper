package com.company.meetinghelper.participant.api;

import com.company.meetinghelper.participant.api.dto.request.CreateParticipantRequest;
import com.company.meetinghelper.participant.api.dto.response.ParticipantResult;
import com.company.meetinghelper.participant.service.ParticipantService;
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
    ParticipantResult create(
            @PathVariable String meetingId,
            @Valid @RequestBody CreateParticipantRequest request
    ) {
        return participantService.create(meetingId, request);
    }

    @DeleteMapping("/{participantId}")
    ResponseEntity<Void> delete(@PathVariable String meetingId, @PathVariable String participantId) {
        participantService.delete(meetingId, participantId);
        return ResponseEntity.noContent().build();
    }
}
