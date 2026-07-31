package com.company.meetinghelper.seating.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.AuditedEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PlanItemEntity 类。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("t_plan_items")
public class PlanItemEntity extends AuditedEntity {
    @TableField("plan_id")
    private String planId;

    @TableField("item_type")
    private PlanItemType itemType;

    @TableField("participant_id")
    private String participantId;
    private String label;
    private boolean locked;
    @TableField("background_color")
    private String backgroundColor;
    @TableField("text_color")
    private String textColor;
    private boolean bold;
}
