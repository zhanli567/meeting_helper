package com.company.meetinghelper.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Represents the audited entity class.
 */
@Getter
@Setter
public abstract class AuditedEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("created_by_id")
    private String createdById;

    @TableField("created_by_name")
    private String createdByName;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_by_id")
    private String updatedById;

    @TableField("updated_by_name")
    private String updatedByName;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    @TableField("row_version")
    private long rowVersion;
}

