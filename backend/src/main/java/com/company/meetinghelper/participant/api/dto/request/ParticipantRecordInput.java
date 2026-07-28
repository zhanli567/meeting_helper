package com.company.meetinghelper.participant.api.dto.request;

import java.util.Map;

public record ParticipantRecordInput(String id, Map<String, String> attributes) {
}
