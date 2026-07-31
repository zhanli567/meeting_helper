package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Represents the save assignments request record.
 *
 * @param assignments assignments
 */
public record SaveAssignmentsRequest(
        @NotNull List<@Valid AssignmentInput> assignments
) {
}
