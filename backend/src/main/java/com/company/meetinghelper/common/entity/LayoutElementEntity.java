package com.company.meetinghelper.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.company.meetinghelper.venue.entity.ElementKind;
import lombok.Getter;
import lombok.Setter;

/**
 * 画布布局元素的公共持久化字段。
 */
@Getter
@Setter
public abstract class LayoutElementEntity extends AuditedEntity {
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
