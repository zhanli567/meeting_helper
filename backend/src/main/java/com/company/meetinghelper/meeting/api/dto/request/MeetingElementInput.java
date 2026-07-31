package com.company.meetinghelper.meeting.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * MeetingElementInput 数据结构。
 * @param id id 参数。
 * @param kind kind 参数。
 * @param name name 参数。
 * @param row row 参数。
 * @param column column 参数。
 * @param rowSpan rowSpan 参数。
 * @param columnSpan columnSpan 参数。
 * @param fillColor fillColor 参数。
 */
public record MeetingElementInput(
        String id,
        @NotBlank String kind,
        @NotBlank String name,
        @Min(1) int row,
        @Min(1) int column,
        @Min(1) int rowSpan,
        @Min(1) int columnSpan,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String fillColor
) {
}
