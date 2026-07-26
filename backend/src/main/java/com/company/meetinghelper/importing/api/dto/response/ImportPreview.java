package com.company.meetinghelper.importing.api.dto.response;

import java.util.List;

/**
 * 通用人员导入的可提交预览。
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
