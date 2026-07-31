package com.company.meetinghelper.venue.api.dto.request;

import com.company.meetinghelper.venue.api.dto.ElementInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Represents the create venue request record.
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
 * @param gridRows grid rows
 * @param gridColumns grid columns
 * @param elements elements
 */
public record CreateVenueRequest(
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
        @Min(5) int gridRows,
        @Min(5) int gridColumns,
        @NotNull List<@Valid ElementInput> elements
) {
}
