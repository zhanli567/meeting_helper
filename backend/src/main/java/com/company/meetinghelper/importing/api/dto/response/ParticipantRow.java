package com.company.meetinghelper.importing.api.dto.response;

import java.util.Map;

/**
 * 单条人员动态记录的导入预览。
 */
public record ParticipantRow(
        int sourceRow,
        String employeeNo,
        String name,
        Map<String, String> attributes,
        String expectedAction
) {
}
