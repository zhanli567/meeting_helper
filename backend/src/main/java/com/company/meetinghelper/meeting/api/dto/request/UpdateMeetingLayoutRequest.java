package com.company.meetinghelper.meeting.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * UpdateMeetingLayoutRequest 数据结构。
 * @param gridRows gridRows 参数。
 * @param gridColumns gridColumns 参数。
 * @param elements elements 参数。
 */
public record UpdateMeetingLayoutRequest(
        @Min(5) int gridRows,
        @Min(5) int gridColumns,
        @NotNull List<@Valid MeetingElementInput> elements
) {
}
