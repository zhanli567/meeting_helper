package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * AssignmentRequest 数据结构。
 * @param participantId participantId 参数。
 * @param targetElementId targetElementId 参数。
 */
public record AssignmentRequest(
        @NotBlank String participantId,
        @NotBlank String targetElementId
) {
}
