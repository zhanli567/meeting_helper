package com.company.meetinghelper.meeting.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.AuditedEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MeetingEntity 类。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("t_meetings")
public class MeetingEntity extends AuditedEntity {
    private String name;
    private String status;
    @TableField("venue_template_id")
    private String venueTemplateId;
    @TableField("layout_name")
    private String layoutName;
    @TableField("grid_rows")
    private int gridRows;
    @TableField("grid_columns")
    private int gridColumns;
    @TableField("layout_version")
    private int layoutVersion;
}
