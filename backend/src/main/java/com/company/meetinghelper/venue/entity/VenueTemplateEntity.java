package com.company.meetinghelper.venue.entity;

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
public class VenueTemplateEntity extends AuditedEntity {
    private String name;
    private String description;
    private int gridRows;
    private int gridColumns;
    private int cellSize;
    private int versionNo;
    private boolean preset;

    @Enumerated(EnumType.STRING)
    private FrontDirection frontDirection;
}
