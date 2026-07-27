package com.company.meetinghelper.venue.api.dto.response;

import com.company.meetinghelper.venue.api.dto.ElementInput;
import java.util.List;

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
