package com.company.meetinghelper.seating.entity;

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
@Table(name = "t_plan_item_targets")
@TableName("t_plan_item_targets")
public class PlanItemTargetEntity extends AuditedEntity {
    @TableField("plan_item_id")
    private String planItemId;
    @TableField("meeting_element_id")
    private String meetingElementId;
}
