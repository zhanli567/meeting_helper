package com.company.meetinghelper.participant.api.dto.response;

public record ParticipantResult(
        String id,
        String employeeNo,
        String name,
        String action,
        String message
) {
    public ParticipantResult(String id, String employeeNo, String name) {
        this(id, employeeNo, name, "CREATED", "人员已新增");
    }
}
