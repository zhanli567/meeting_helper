package com.company.meetinghelper.participant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.AuditedEntity;
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
@TableName("t_participants")
public class ParticipantEntity extends AuditedEntity {
    @TableField("meeting_id")
    private String meetingId;
    @TableField("employee_no")
    private String employeeNo;
    private String name;

    @Enumerated(EnumType.STRING)
    @TableField("attendance_status")
    private AttendanceStatus attendanceStatus = AttendanceStatus.PRESENT;

    private boolean locked;
}
