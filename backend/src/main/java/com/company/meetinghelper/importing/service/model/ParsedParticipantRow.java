package com.company.meetinghelper.importing.service.model;

import java.util.Map;

/**
 * ParsedParticipantRow 数据结构。
 * @param sourceRow sourceRow 参数。
 * @param employeeNo employeeNo 参数。
 * @param name name 参数。
 * @param attributes attributes 参数。
 */
public record ParsedParticipantRow(
        int sourceRow,
        String employeeNo,
        String name,
        Map<String, String> attributes
) {
}
