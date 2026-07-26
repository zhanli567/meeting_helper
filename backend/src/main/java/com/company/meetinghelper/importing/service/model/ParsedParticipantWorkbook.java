package com.company.meetinghelper.importing.service.model;

import java.util.List;

/**
 * 通用人员工作簿的解析结果及结构化冲突标记。
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
