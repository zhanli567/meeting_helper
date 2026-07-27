package com.company.meetinghelper.venue.api.dto.response;

import java.util.List;

public record VenuePage(
        List<VenueSummary> records,
        long total,
        int pageNum,
        int pageSize
) {
}
