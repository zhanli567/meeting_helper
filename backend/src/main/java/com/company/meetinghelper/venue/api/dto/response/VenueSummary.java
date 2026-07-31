package com.company.meetinghelper.venue.api.dto.response;

import java.time.OffsetDateTime;

/**
 * Represents the venue summary record.
 *
 * @param id id
 * @param location location
 * @param campus campus
 * @param manualCapacity manual capacity
 * @param seatCount seat count
 * @param usable usable
 * @param updatedByName updated by name
 * @param updatedAt updated at
 * @param rowVersion row version
 */
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
