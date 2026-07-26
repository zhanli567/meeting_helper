package com.company.meetinghelper.importing.service.model;

import java.util.Map;

public record ParsedParticipantRow(
        int sourceRow,
        String employeeNo,
        String name,
        Map<String, String> attributes
) {
}
