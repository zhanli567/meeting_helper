package com.company.meetinghelper.importing.service.model;

import java.util.List;

/**
 * Represents the parsed participant workbook record.
 *
 * @param fieldNames field names
 * @param rows rows
 * @param totalRows total rows
 * @param ignoredDuplicateRows ignored duplicate rows
 * @param errors errors
 * @param employeeNameConflict employee name conflict
 */
public record ParsedParticipantWorkbook(
        List<String> fieldNames,
        List<ParsedParticipantRow> rows,
        int totalRows,
        int ignoredDuplicateRows,
        List<String> errors,
        boolean employeeNameConflict
) {
}
