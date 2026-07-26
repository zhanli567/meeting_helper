package com.company.meetinghelper.participant.entity;

import com.company.meetinghelper.common.entity.AuditedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_meeting_participant_fields")
public class MeetingParticipantFieldEntity extends AuditedEntity {
    private String meetingId;
    private String fieldName;
    private int sortOrder;
}
