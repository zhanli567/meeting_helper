package com.company.meetinghelper.meeting.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.AuditedEntity;
import com.company.meetinghelper.venue.entity.ElementType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("t_meeting_elements")
public class MeetingElementEntity extends AuditedEntity {
    @TableField("meeting_id")
    private String meetingId;
    @TableField("source_element_id")
    private String sourceElementId;

    @TableField("element_type")
    private ElementType elementType;

    private String code;
    private String label;
    @TableField("grid_row")
    private int gridRow;
    @TableField("grid_column")
    private int gridColumn;
    @TableField("row_span")
    private int rowSpan;
    @TableField("column_span")
    private int columnSpan;
    private int rotation;
    private int capacity;
    private boolean assignable;
    private boolean walkable;
    @TableField("group_code")
    private String groupCode;
    @TableField("group_label")
    private String groupLabel;
    @TableField("sequence_no")
    private Integer sequenceNo;
    @TableField("background_color")
    private String backgroundColor;
    @TableField("border_color")
    private String borderColor;
}
