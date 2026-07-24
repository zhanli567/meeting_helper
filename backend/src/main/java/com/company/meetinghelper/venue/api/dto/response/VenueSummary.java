package com.company.meetinghelper.venue.api.dto.response;

public record VenueSummary(
        String id,
        String name,
        String description,
        int gridRows,
        int gridColumns,
        int versionNo,
        boolean preset,
        long seatCount
) {
}
