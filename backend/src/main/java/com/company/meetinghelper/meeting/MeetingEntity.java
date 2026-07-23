package com.company.meetinghelper.meeting;

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
@Table(name = "t_meetings")
public class MeetingEntity extends AuditedEntity {
    private String name;
    private String status;
    private String venueTemplateId;
    private String layoutName;
    private int gridRows;
    private int gridColumns;
    private int cellSize;
    private int layoutVersion;
}
