package com.company.meetinghelper.meeting.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Represents the update meeting name request record.
 *
 * @param name name
 */
public record UpdateMeetingNameRequest(
        @NotBlank String name
) {
}
