package com.company.meetinghelper.participant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.AuditedEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("t_participant_records")
public class ParticipantRecordEntity extends AuditedEntity {
    @TableField("participant_id")
    private String participantId;
    @TableField("record_order")
    private int recordOrder;

    @TableField("attributes_json")
    private String attributesJson;
}
