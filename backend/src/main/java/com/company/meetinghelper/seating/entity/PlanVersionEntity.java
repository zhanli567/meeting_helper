package com.company.meetinghelper.seating.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.AuditedEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the plan version entity class.
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("t_plan_versions")
public class PlanVersionEntity extends AuditedEntity {
    @TableField("plan_id")
    private String planId;
    @TableField("version_no")
    private int versionNo;
    @TableField("version_name")
    private String versionName;
    @TableField("change_note")
    private String changeNote;
    private boolean automatic;

    @TableField("snapshot_json")
    private String snapshotJson;

    @TableField("assigned_count")
    private int assignedCount;
    @TableField("unassigned_count")
    private int unassignedCount;
}
