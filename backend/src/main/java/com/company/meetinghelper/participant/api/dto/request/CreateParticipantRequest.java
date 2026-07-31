package com.company.meetinghelper.participant.api.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Represents the create participant request record.
 *
 * @param employeeNo employee no
 * @param name name
 * @param attributes attributes
 * @param targetElementId target element id
 */
public record CreateParticipantRequest(
        @NotBlank String employeeNo,
        @NotBlank String name,
        Map<String, String> attributes,
        String targetElementId
) {
}
