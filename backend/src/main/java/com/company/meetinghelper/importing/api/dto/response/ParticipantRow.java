package com.company.meetinghelper.importing.api.dto.response;

import java.util.Map;

public record ParticipantRow(
        int sourceRow,
        String employeeNo,
        String name,
        Integer level,
        String department,
        String participantType,
        String tags,
        Map<String, String> attributes
) {
}
