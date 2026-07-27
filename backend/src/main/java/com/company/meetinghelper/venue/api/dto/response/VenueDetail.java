package com.company.meetinghelper.venue.api.dto.response;

import java.time.OffsetDateTime;

public record VenueDetail(
        String id,
        String location,
        String campus,
        String mainScreenResolution,
        String stageDimensions,
        Integer manualCapacity,
        int seatCount,
        String contactInfo,
        String bookingUrl,
        String meetingRoomFunctions,
        String servicesProvided,
        String description,
        String remarks,
        int gridRows,
        int gridColumns,
        String createdByName,
        OffsetDateTime createdAt,
        String updatedByName,
        OffsetDateTime updatedAt,
        long rowVersion
) {
}
