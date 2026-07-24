package com.company.meetinghelper.award.entity;

import com.company.meetinghelper.common.entity.AuditedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_award_records")
public class AwardRecordEntity extends AuditedEntity {
    private String participantId;
    private int batchOrder;
    private String batchName;
    private String awardName;
    private String awardLevel;
    private String projectName;
    private Integer teamSize;
}
