package com.company.meetinghelper.importing.service.model;

import java.util.List;

/**
 * ParsedParticipantWorkbook 数据结构。
 * @param fieldNames fieldNames 参数。
 * @param rows rows 参数。
 * @param totalRows totalRows 参数。
 * @param ignoredDuplicateRows ignoredDuplicateRows 参数。
 * @param errors errors 参数。
 * @param employeeNameConflict employeeNameConflict 参数。
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
