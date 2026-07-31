package com.company.meetinghelper.meeting.api.dto.response;

import java.time.OffsetDateTime;

/**
 * Represents the meeting summary record.
 *
 * @param id id
 * @param name name
 * @param status status
 * @param layoutName layout name
 * @param updatedAt updated at
 * @param updatedByName updated by name
 */
public record MeetingSummary(
        String id,
        String name,
        String status,
        String layoutName,
        OffsetDateTime updatedAt,
        String updatedByName
) {
}
