package com.company.meetinghelper.participant.api.dto.response;

/**
 * ParticipantResult 数据结构。
 * @param id id 参数。
 * @param employeeNo employeeNo 参数。
 * @param name name 参数。
 * @param action action 参数。
 * @param message message 参数。
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
