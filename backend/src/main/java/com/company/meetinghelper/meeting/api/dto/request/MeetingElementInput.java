package com.company.meetinghelper.meeting.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MeetingElementInput(
        String id,
        @NotBlank String kind,
        @NotBlank String name,
        @Min(1) int row,
        @Min(1) int column,
        @Min(1) int rowSpan,
        @Min(1) int columnSpan,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String fillColor,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String borderColor
) {
}
