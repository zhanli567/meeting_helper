package com.company.meetinghelper.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public abstract class AuditedEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @Column(nullable = false, updatable = false)
    @TableField("created_by_id")
    private String createdById;

    @Column(nullable = false, updatable = false)
    @TableField("created_by_name")
    private String createdByName;

    @Column(nullable = false, updatable = false)
    @TableField("created_at")
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    @TableField("updated_by_id")
    private String updatedById;

    @Column(nullable = false)
    @TableField("updated_by_name")
    private String updatedByName;

    @Column(nullable = false)
    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    @Column(nullable = false)
    @TableLogic
    @TableField("deleted")
    private boolean deleted;

    @Version
    @Column(nullable = false)
    @TableField("row_version")
    private long rowVersion;

    @PrePersist
    protected void beforeInsert() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (createdById == null) {
            createdById = "demo-secretary";
            createdByName = "演示秘书";
        }
        if (updatedById == null) {
            updatedById = createdById;
            updatedByName = createdByName;
        }
    }

    @PreUpdate
    protected void beforeUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (updatedById == null) {
            updatedById = "demo-secretary";
            updatedByName = "演示秘书";
        }
    }
}

