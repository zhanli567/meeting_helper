package com.company.meetinghelper.venue.api.dto.response;

import java.util.List;

/**
 * VenuePage 数据结构。
 * @param records records 参数。
 * @param total total 参数。
 * @param pageNum pageNum 参数。
 * @param pageSize pageSize 参数。
 */
public record VenuePage(
        List<VenueSummary> records,
        long total,
        int pageNum,
        int pageSize
) {
}
