package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AssignmentInput(
        @NotBlank String participantId,
        @NotBlank String targetElementId
) {
}
