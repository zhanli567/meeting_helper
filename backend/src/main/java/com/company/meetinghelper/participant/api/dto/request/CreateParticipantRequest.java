package com.company.meetinghelper.participant.api.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * CreateParticipantRequest 数据结构。
 * @param employeeNo employeeNo 参数。
 * @param name name 参数。
 * @param attributes attributes 参数。
 * @param targetElementId targetElementId 参数。
 */
public record CreateParticipantRequest(
        @NotBlank String employeeNo,
        @NotBlank String name,
        Map<String, String> attributes,
        String targetElementId
) {
}
