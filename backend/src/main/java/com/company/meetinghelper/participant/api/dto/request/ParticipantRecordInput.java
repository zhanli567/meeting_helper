package com.company.meetinghelper.participant.api.dto.request;

import java.util.Map;

/**
 * Represents the participant record input record.
 *
 * @param id id
 * @param attributes attributes
 */
public record ParticipantRecordInput(String id, Map<String, String> attributes) {
}
