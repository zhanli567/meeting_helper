package com.company.meetinghelper.importing.api.dto.response;

/**
 * Represents the commit result record.
 *
 * @param newParticipants new participants
 * @param mergedRecords merged records
 * @param appendedRecords appended records
 * @param skippedRecords skipped records
 */
public record CommitResult(
        int newParticipants,
        int mergedRecords,
        int appendedRecords,
        int skippedRecords
) {
}
