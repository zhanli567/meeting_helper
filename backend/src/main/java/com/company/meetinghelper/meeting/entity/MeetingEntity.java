package com.company.meetinghelper.meeting.entity;

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
@Table(name = "t_meetings")
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
    @TableField("cell_size")
    private int cellSize;
    @TableField("layout_version")
    private int layoutVersion;
}
