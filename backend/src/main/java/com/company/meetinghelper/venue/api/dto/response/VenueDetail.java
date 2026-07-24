package com.company.meetinghelper.venue.api.dto.response;

import com.company.meetinghelper.venue.api.dto.ElementInput;

import java.util.List;

public record VenueDetail(
        String id,
        String name,
        String description,
        int gridRows,
        int gridColumns,
        int cellSize,
        int versionNo,
        boolean preset,
        String frontDirection,
        List<ElementInput> elements
) {
}
