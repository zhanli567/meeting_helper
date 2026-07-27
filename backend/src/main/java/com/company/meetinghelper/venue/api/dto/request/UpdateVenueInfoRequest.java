package com.company.meetinghelper.venue.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

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
