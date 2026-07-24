package com.company.meetinghelper.participant.entity;

import com.company.meetinghelper.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_participants")
public class ParticipantEntity extends AuditedEntity {
    private String meetingId;
    private String employeeNo;
    private String name;
    private Integer levelValue;
    private String department;
    private String participantType;
    private String tags;

    @Enumerated(EnumType.STRING)
    private AttendanceStatus attendanceStatus = AttendanceStatus.PRESENT;

    @Column(columnDefinition = "text")
    private String customAttributesJson;

    private boolean locked;
}
