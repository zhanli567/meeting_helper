package com.company.meetinghelper.meeting.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Represents the create meeting request record.
 *
 * @param name name
 * @param venueTemplateId venue template id
 */
public record CreateMeetingRequest(
        @NotBlank String name,
        @NotBlank String venueTemplateId
) {
}
