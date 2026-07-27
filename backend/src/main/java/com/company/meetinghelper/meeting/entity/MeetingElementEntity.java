package com.company.meetinghelper.meeting.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.AuditedEntity;
import com.company.meetinghelper.venue.entity.ElementKind;
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
    @TableField("element_kind")
    private ElementKind elementKind;
    @TableField("element_name")
    private String elementName;
    @TableField("start_row")
    private int startRow;
    @TableField("start_column")
    private int startColumn;
    @TableField("row_span")
    private int rowSpan;
    @TableField("column_span")
    private int columnSpan;
    @TableField("fill_color")
    private String fillColor;
    @TableField("border_color")
    private String borderColor;
}
