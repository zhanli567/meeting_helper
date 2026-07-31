package com.company.meetinghelper.venue.api.dto.response;

import com.company.meetinghelper.venue.api.dto.ElementInput;
import java.util.List;

/**
 * VenueLayout 数据结构。
 * @param id id 参数。
 * @param location location 参数。
 * @param manualCapacity manualCapacity 参数。
 * @param gridRows gridRows 参数。
 * @param gridColumns gridColumns 参数。
 * @param seatCount seatCount 参数。
 * @param rowVersion rowVersion 参数。
 * @param elements elements 参数。
 */
public record VenueLayout(
        String id,
        String location,
        Integer manualCapacity,
        int gridRows,
        int gridColumns,
        int seatCount,
        long rowVersion,
        List<ElementInput> elements
) {
}
