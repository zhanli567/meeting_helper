package com.company.meetinghelper.venue.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Represents the update venue info request record.
 *
 * @param location location
 * @param campus campus
 * @param mainScreenResolution main screen resolution
 * @param stageDimensions stage dimensions
 * @param manualCapacity manual capacity
 * @param contactInfo contact info
 * @param bookingUrl booking url
 * @param meetingRoomFunctions meeting room functions
 * @param servicesProvided services provided
 * @param description description
 * @param remarks remarks
 * @param rowVersion row version
 */
public record UpdateVenueInfoRequest(
        @NotBlank @Size(max = 200) String location,
        @Size(max = 120) String campus,
        @Size(max = 80) String mainScreenResolution,
        @Size(max = 80) String stageDimensions,
        @PositiveOrZero Integer manualCapacity,
        @Size(max = 500) String contactInfo,
        @Size(max = 1000) String bookingUrl,
        @Size(max = 2000) String meetingRoomFunctions,
        @Size(max = 2000) String servicesProvided,
        @Size(max = 2000) String description,
        @Size(max = 2000) String remarks,
        @PositiveOrZero long rowVersion
) {
}
