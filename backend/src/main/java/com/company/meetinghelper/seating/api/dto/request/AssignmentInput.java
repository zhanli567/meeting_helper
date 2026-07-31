package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * AssignmentInput 数据结构。
 * @param participantId participantId 参数。
 * @param targetElementId targetElementId 参数。
 */
public record AssignmentInput(
        @NotBlank String participantId,
        @NotBlank String targetElementId
) {
}
