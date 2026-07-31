package com.company.meetinghelper.participant.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * UpdateParticipantRequest 数据结构。
 * @param name name 参数。
 * @param records records 参数。
 * @param fieldNames fieldNames 参数。
 */
public record UpdateParticipantRequest(
        @NotBlank String name,
        List<ParticipantRecordInput> records,
        List<String> fieldNames
) {
}
