package com.company.meetinghelper.importing.service.model;

import com.company.meetinghelper.importing.api.dto.response.AwardRow;
import com.company.meetinghelper.importing.api.dto.response.ParticipantRow;

import java.util.List;

public record ParsedWorkbook(
        List<ParticipantRow> participants,
        List<AwardRow> awards,
        List<String> errors
) {
}
