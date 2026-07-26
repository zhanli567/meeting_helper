package com.company.meetinghelper.participant.entity;

import com.company.meetinghelper.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_participant_records")
public class ParticipantRecordEntity extends AuditedEntity {
    private String participantId;
    private int recordOrder;

    @Column(columnDefinition = "text")
    private String attributesJson;
}
