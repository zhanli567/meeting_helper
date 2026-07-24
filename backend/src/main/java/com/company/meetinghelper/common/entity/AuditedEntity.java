package com.company.meetinghelper.common.entity;

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
    private String id;

    @Column(nullable = false, updatable = false)
    private String createdById;

    @Column(nullable = false, updatable = false)
    private String createdByName;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private String updatedById;

    @Column(nullable = false)
    private String updatedByName;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Column(nullable = false)
    private boolean deleted;

    @Version
    @Column(nullable = false)
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

