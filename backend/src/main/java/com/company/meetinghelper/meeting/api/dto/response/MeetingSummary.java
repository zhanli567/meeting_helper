package com.company.meetinghelper.meeting.api.dto.response;

import java.time.OffsetDateTime;

/**
 * MeetingSummary 数据结构。
 * @param id id 参数。
 * @param name name 参数。
 * @param status status 参数。
 * @param layoutName layoutName 参数。
 * @param updatedAt updatedAt 参数。
 * @param updatedByName updatedByName 参数。
 */
public record MeetingSummary(
        String id,
        String name,
        String status,
        String layoutName,
        OffsetDateTime updatedAt,
        String updatedByName
) {
}
