package com.company.meetinghelper.importing.api.dto.response;

import java.util.Map;

/**
 * ParticipantRow 数据结构。
 * @param sourceRow sourceRow 参数。
 * @param employeeNo employeeNo 参数。
 * @param name name 参数。
 * @param attributes attributes 参数。
 * @param expectedAction expectedAction 参数。
 */
public record ParticipantRow(
        int sourceRow,
        String employeeNo,
        String name,
        Map<String, String> attributes,
        String expectedAction
) {
}
