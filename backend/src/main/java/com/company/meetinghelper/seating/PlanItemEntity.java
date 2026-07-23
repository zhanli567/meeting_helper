package com.company.meetinghelper.seating;

import com.company.meetinghelper.common.AuditedEntity;
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
@Table(name = "plan_items")
public class PlanItemEntity extends AuditedEntity {
    private String planId;

    @Enumerated(EnumType.STRING)
    private PlanItemType itemType;

    private String participantId;
    private String label;
    private boolean locked;
    private String backgroundColor;
    private String textColor;
    private boolean bold;
}

