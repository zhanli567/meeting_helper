package com.company.meetinghelper.participant.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record CreateParticipantRequest(
        @NotBlank
        @Pattern(
                regexp = "^(?:[0-9]{8}|[a-z][0-9]{8})$",
                message = "工号必须为8位数字或1个小写字母加8位数字"
        )
        String employeeNo,
        @NotBlank String name,
        Integer level,
        String department,
        String participantType,
        String tags,
        Map<String, String> attributes
) {
}
