package com.company.meetinghelper.participant.api.dto.request;

import java.util.Map;

/**
 * ParticipantRecordInput 数据结构。
 * @param id id 参数。
 * @param attributes attributes 参数。
 */
public record ParticipantRecordInput(String id, Map<String, String> attributes) {
}
