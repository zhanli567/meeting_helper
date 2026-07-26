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
@Table(name = "t_venue_templates")
@TableName("t_venue_templates")
public class VenueTemplateEntity extends AuditedEntity {
    private String name;
    private String description;
    @TableField("grid_rows")
    private int gridRows;
    @TableField("grid_columns")
    private int gridColumns;
    @TableField("cell_size")
    private int cellSize;
    @TableField("version_no")
    private int versionNo;
    private boolean preset;

    @Enumerated(EnumType.STRING)
    @TableField("front_direction")
    private FrontDirection frontDirection;
}
