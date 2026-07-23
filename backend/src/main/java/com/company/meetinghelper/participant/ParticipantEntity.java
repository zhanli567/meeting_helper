package com.company.meetinghelper.participant;

import com.company.meetinghelper.common.AuditedEntity;
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
@Table(name = "participants")
public class ParticipantEntity extends AuditedEntity {
    private String meetingId;
    private String employeeNo;
    private String name;
    private Integer levelValue;
    private String department;
    private String participantType;
    private String tags;

    @Column(columnDefinition = "text")
    private String customAttributesJson;

    private boolean locked;
}
