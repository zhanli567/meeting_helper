package com.company.meetinghelper.importing.api.dto.response;

import java.util.List;

public record ImportPreview(
        String token,
        String templateCode,
        int participantRowCount,
        int awardRowCount,
        List<ParticipantRow> uniqueParticipants,
        List<DuplicateGroup> duplicateGroups,
        List<String> errors
) {
}
