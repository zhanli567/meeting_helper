package com.company.meetinghelper.venue.api.dto.response;

import java.time.OffsetDateTime;

public record VenueSummary(
        String id,
        String location,
        String campus,
        Integer manualCapacity,
        int seatCount,
        boolean usable,
        String updatedByName,
        OffsetDateTime updatedAt,
        long rowVersion
) {
}
