package com.company.meetinghelper.venue.api.dto.response;

import java.time.OffsetDateTime;

/**
 * VenueSummary 数据结构。
 * @param id id 参数。
 * @param location location 参数。
 * @param campus campus 参数。
 * @param manualCapacity manualCapacity 参数。
 * @param seatCount seatCount 参数。
 * @param usable usable 参数。
 * @param updatedByName updatedByName 参数。
 * @param updatedAt updatedAt 参数。
 * @param rowVersion rowVersion 参数。
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
