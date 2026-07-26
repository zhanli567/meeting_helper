package com.company.meetinghelper.participant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("t_meeting_participant_fields")
public class MeetingParticipantFieldEntity extends AuditedEntity {
    @TableField("meeting_id")
    private String meetingId;
    @TableField("field_name")
    private String fieldName;
    @TableField("sort_order")
    private int sortOrder;
}
