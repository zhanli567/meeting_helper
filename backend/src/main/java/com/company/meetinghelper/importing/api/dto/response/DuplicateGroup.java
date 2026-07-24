package com.company.meetinghelper.importing.api.dto.response;

import java.util.List;

public record DuplicateGroup(String employeeNo, List<ParticipantRow> candidates) {
}
