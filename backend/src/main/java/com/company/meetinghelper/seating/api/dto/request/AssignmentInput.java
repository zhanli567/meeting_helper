package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Represents the assignment input record.
 *
 * @param participantId participant id
 * @param targetElementId target element id
 */
public record AssignmentInput(
        @NotBlank String participantId,
        @NotBlank String targetElementId
) {
}
