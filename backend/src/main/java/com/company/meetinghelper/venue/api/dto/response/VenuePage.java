package com.company.meetinghelper.venue.api.dto.response;

import java.util.List;

/**
 * Represents the venue page record.
 *
 * @param records records
 * @param total total
 * @param pageNum page num
 * @param pageSize page size
 */
public record VenuePage(
        List<VenueSummary> records,
        long total,
        int pageNum,
        int pageSize
) {
}
