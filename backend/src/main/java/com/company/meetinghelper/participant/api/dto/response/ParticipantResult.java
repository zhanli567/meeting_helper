package com.company.meetinghelper.participant.api.dto.response;

/**
 * Represents the participant result record.
 *
 * @param id id
 * @param employeeNo employee no
 * @param name name
 * @param action action
 * @param message message
 */
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
