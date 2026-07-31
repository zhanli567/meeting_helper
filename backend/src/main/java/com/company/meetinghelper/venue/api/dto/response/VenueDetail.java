package com.company.meetinghelper.venue.api.dto.response;

import java.time.OffsetDateTime;

/**
 * Represents the venue detail record.
 *
 * @param id id
 * @param location location
 * @param campus campus
 * @param mainScreenResolution main screen resolution
 * @param stageDimensions stage dimensions
 * @param manualCapacity manual capacity
 * @param seatCount seat count
 * @param contactInfo contact info
 * @param bookingUrl booking url
 * @param meetingRoomFunctions meeting room functions
 * @param servicesProvided services provided
 * @param description description
 * @param remarks remarks
 * @param gridRows grid rows
 * @param gridColumns grid columns
 * @param createdByName created by name
 * @param createdAt created at
 * @param updatedByName updated by name
 * @param updatedAt updated at
 * @param rowVersion row version
 */
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
