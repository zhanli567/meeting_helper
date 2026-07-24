package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveAssignmentsRequest(
        @NotNull List<@Valid AssignmentInput> assignments
) {
}
