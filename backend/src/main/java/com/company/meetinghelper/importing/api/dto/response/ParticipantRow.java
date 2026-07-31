package com.company.meetinghelper.importing.api.dto.response;

import java.util.Map;

/**
 * Represents the participant row record.
 *
 * @param sourceRow source row
 * @param employeeNo employee no
 * @param name name
 * @param attributes attributes
 * @param expectedAction expected action
 */
public record ParticipantRow(
        int sourceRow,
        String employeeNo,
        String name,
        Map<String, String> attributes,
        String expectedAction
) {
}
