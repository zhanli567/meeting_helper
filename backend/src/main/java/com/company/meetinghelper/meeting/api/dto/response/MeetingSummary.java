package com.company.meetinghelper.meeting.api.dto.response;

import java.time.OffsetDateTime;

public record MeetingSummary(
        String id,
        String name,
        String status,
        String layoutName,
        OffsetDateTime updatedAt,
        String updatedByName
) {
}
