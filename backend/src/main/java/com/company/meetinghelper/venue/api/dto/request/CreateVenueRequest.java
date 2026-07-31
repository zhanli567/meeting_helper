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
 * CreateVenueRequest 数据结构。
 * @param location location 参数。
 * @param campus campus 参数。
 * @param mainScreenResolution mainScreenResolution 参数。
 * @param stageDimensions stageDimensions 参数。
 * @param manualCapacity manualCapacity 参数。
 * @param contactInfo contactInfo 参数。
 * @param bookingUrl bookingUrl 参数。
 * @param meetingRoomFunctions meetingRoomFunctions 参数。
 * @param servicesProvided servicesProvided 参数。
 * @param description description 参数。
 * @param remarks remarks 参数。
 * @param gridRows gridRows 参数。
 * @param gridColumns gridColumns 参数。
 * @param elements elements 参数。
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
