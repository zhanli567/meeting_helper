package com.company.meetinghelper.venue.api.dto.response;

import com.company.meetinghelper.venue.api.dto.ElementInput;
import java.util.List;

/**
 * Represents the venue layout record.
 *
 * @param id id
 * @param location location
 * @param manualCapacity manual capacity
 * @param gridRows grid rows
 * @param gridColumns grid columns
 * @param seatCount seat count
 * @param rowVersion row version
 * @param elements elements
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
