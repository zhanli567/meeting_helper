package com.company.meetinghelper.participant.api.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CreateParticipantRequest(
        @NotBlank String employeeNo,
        @NotBlank String name,
        Map<String, String> attributes,
        String targetElementId
) {
}
