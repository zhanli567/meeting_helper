package com.company.meetinghelper.participant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.AuditedEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the participant entity class.
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("t_participants")
public class ParticipantEntity extends AuditedEntity {
    @TableField("meeting_id")
    private String meetingId;
    @TableField("employee_no")
    private String employeeNo;
    private String name;

    @TableField("attendance_status")
    private AttendanceStatus attendanceStatus = AttendanceStatus.PRESENT;

    private boolean locked;
}
