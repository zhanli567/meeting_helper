package com.company.meetinghelper.meeting.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * UpdateMeetingNameRequest 数据结构。
 * @param name name 参数。
 */
public record UpdateMeetingNameRequest(
        @NotBlank String name
) {
}
