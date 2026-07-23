package com.company.meetinghelper.venue;

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
@Table(name = "t_venue_elements")
public class VenueElementEntity extends AuditedEntity {
    private String venueTemplateId;

    @Enumerated(EnumType.STRING)
    private ElementType elementType;

    private String code;
    private String label;
    private int gridRow;
    private int gridColumn;
    private int rowSpan;
    private int columnSpan;
    private int rotation;
    private int capacity;
    private boolean assignable;
    private boolean walkable;
    private String groupCode;
    private String groupLabel;
    private Integer sequenceNo;
    private String backgroundColor;
    private String borderColor;
}
