package com.company.meetinghelper.seating.entity;

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
@Table(name = "t_plan_items")
@TableName("t_plan_items")
public class PlanItemEntity extends AuditedEntity {
    @TableField("plan_id")
    private String planId;

    @Enumerated(EnumType.STRING)
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
