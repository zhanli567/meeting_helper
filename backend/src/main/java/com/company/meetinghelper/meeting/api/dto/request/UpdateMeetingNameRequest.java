package com.company.meetinghelper.meeting.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateMeetingNameRequest(
        @NotBlank String name
) {
}
