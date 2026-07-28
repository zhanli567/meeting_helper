package com.company.meetinghelper.participant.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record UpdateParticipantRequest(
        @NotBlank String name,
        List<ParticipantRecordInput> records
) {
}
