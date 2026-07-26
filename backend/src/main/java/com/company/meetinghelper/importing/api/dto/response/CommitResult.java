package com.company.meetinghelper.importing.api.dto.response;

/**
 * 通用人员导入提交的分类统计。
 */
public record CommitResult(
        int newParticipants,
        int mergedRecords,
        int appendedRecords,
        int skippedRecords
) {
}
