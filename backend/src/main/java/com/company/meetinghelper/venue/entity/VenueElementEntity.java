package com.company.meetinghelper.venue.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.AuditedEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the venue element entity class.
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("t_venue_elements")
public class VenueElementEntity extends AuditedEntity {
    @TableField("venue_template_id")
    private String venueTemplateId;
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
}
