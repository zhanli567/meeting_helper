package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * SaveAssignmentsRequest 数据结构。
 * @param assignments assignments 参数。
 */
public record SaveAssignmentsRequest(
        @NotNull List<@Valid AssignmentInput> assignments
) {
}
