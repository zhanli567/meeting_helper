package com.company.meetinghelper.meeting.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Represents the update meeting layout request record.
 *
 * @param gridRows grid rows
 * @param gridColumns grid columns
 * @param elements elements
 */
public record UpdateMeetingLayoutRequest(
        @Min(5) int gridRows,
        @Min(5) int gridColumns,
        @NotNull List<@Valid MeetingElementInput> elements
) {
}
