package com.company.meetinghelper.importing.service.model;

import java.util.Map;

/**
 * Represents the parsed participant row record.
 *
 * @param sourceRow source row
 * @param employeeNo employee no
 * @param name name
 * @param attributes attributes
 */
public record ParsedParticipantRow(
        int sourceRow,
        String employeeNo,
        String name,
        Map<String, String> attributes
) {
}
