package com.company.meetinghelper.meeting.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateMeetingRequest(
        @NotBlank String name,
        @NotBlank String venueTemplateId
) {
}
