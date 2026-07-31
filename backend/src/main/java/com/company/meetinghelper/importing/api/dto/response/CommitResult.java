package com.company.meetinghelper.importing.api.dto.response;

/**
 * CommitResult 数据结构。
 * @param newParticipants newParticipants 参数。
 * @param mergedRecords mergedRecords 参数。
 * @param appendedRecords appendedRecords 参数。
 * @param skippedRecords skippedRecords 参数。
 */
public record CommitResult(
        int newParticipants,
        int mergedRecords,
        int appendedRecords,
        int skippedRecords
) {
}
