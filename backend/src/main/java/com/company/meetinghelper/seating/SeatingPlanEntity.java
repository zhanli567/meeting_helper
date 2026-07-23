package com.company.meetinghelper.seating;

import com.company.meetinghelper.common.AuditedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_seating_plans")
public class SeatingPlanEntity extends AuditedEntity {
    private String meetingId;
    private String name;
    private String status;
    private int currentVersionNo;
}
