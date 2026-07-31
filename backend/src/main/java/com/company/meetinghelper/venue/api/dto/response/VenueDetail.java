package com.company.meetinghelper.venue.api.dto.response;

import java.time.OffsetDateTime;

/**
 * VenueDetail 数据结构。
 * @param id id 参数。
 * @param location location 参数。
 * @param campus campus 参数。
 * @param mainScreenResolution mainScreenResolution 参数。
 * @param stageDimensions stageDimensions 参数。
 * @param manualCapacity manualCapacity 参数。
 * @param seatCount seatCount 参数。
 * @param contactInfo contactInfo 参数。
 * @param bookingUrl bookingUrl 参数。
 * @param meetingRoomFunctions meetingRoomFunctions 参数。
 * @param servicesProvided servicesProvided 参数。
 * @param description description 参数。
 * @param remarks remarks 参数。
 * @param gridRows gridRows 参数。
 * @param gridColumns gridColumns 参数。
 * @param createdByName createdByName 参数。
 * @param createdAt createdAt 参数。
 * @param updatedByName updatedByName 参数。
 * @param updatedAt updatedAt 参数。
 * @param rowVersion rowVersion 参数。
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
