package com.company.meetinghelper.participant.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Represents the update participant request record.
 *
 * @param name name
 * @param records records
 * @param fieldNames field names
 */
public record UpdateParticipantRequest(
        @NotBlank String name,
        List<ParticipantRecordInput> records,
        List<String> fieldNames
) {
}
