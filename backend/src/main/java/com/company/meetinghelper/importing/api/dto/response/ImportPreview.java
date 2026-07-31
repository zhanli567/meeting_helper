package com.company.meetinghelper.importing.api.dto.response;

import java.util.List;

/**
 * ImportPreview 数据结构。
 * @param token token 参数。
 * @param totalRows totalRows 参数。
 * @param validRows validRows 参数。
 * @param ignoredDuplicateRows ignoredDuplicateRows 参数。
 * @param participantCount participantCount 参数。
 * @param recordCount recordCount 参数。
 * @param newFields newFields 参数。
 * @param existingFields existingFields 参数。
 * @param rows rows 参数。
 * @param errors errors 参数。
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
