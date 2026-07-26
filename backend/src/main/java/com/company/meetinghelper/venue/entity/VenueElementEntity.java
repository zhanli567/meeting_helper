package com.company.meetinghelper.venue.entity;

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
@Table(name = "t_venue_elements")
@TableName("t_venue_elements")
public class VenueElementEntity extends AuditedEntity {
    @TableField("venue_template_id")
    private String venueTemplateId;

    @Enumerated(EnumType.STRING)
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
