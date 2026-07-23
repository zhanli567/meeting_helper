package com.company.meetinghelper.seating;

import com.company.meetinghelper.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "plan_versions")
public class PlanVersionEntity extends AuditedEntity {
    private String planId;
    private int versionNo;
    private String versionName;
    private String changeNote;
    private boolean automatic;

    @Column(columnDefinition = "text")
    private String snapshotJson;

    private int assignedCount;
    private int unassignedCount;
}
