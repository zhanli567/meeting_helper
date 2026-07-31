package com.company.meetinghelper.importing.api.dto.response;

import java.util.List;

/**
 * Represents the import preview record.
 *
 * @param token token
 * @param totalRows total rows
 * @param validRows valid rows
 * @param ignoredDuplicateRows ignored duplicate rows
 * @param participantCount participant count
 * @param recordCount record count
 * @param newFields new fields
 * @param existingFields existing fields
 * @param rows rows
 * @param errors errors
 */
public record ImportPreview(
        String token,
        int totalRows,
        int validRows,
        int ignoredDuplicateRows,
        int participantCount,
        int recordCount,
        List<String> newFields,
        List<String> existingFields,
        List<ParticipantRow> rows,
        List<String> errors
) {
}
