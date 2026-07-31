package com.company.meetinghelper.meeting.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * CreateMeetingRequest 数据结构。
 * @param name name 参数。
 * @param venueTemplateId venueTemplateId 参数。
 */
public record CreateMeetingRequest(
        @NotBlank String name,
        @NotBlank String venueTemplateId
) {
}
