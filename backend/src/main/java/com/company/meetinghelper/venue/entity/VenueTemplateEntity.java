package com.company.meetinghelper.venue.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.AuditedEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("t_venue_templates")
public class VenueTemplateEntity extends AuditedEntity {
    private String location;
    @TableField("location_key")
    private String locationKey;
    private String campus;
    @TableField("main_screen_resolution")
    private String mainScreenResolution;
    @TableField("stage_dimensions")
    private String stageDimensions;
    @TableField("manual_capacity")
    private Integer manualCapacity;
    @TableField("contact_info")
    private String contactInfo;
    @TableField("booking_url")
    private String bookingUrl;
    @TableField("meeting_room_functions")
    private String meetingRoomFunctions;
    @TableField("services_provided")
    private String servicesProvided;
    private String description;
    private String remarks;
    @TableField("seat_count")
    private int seatCount;
    @TableField("grid_rows")
    private int gridRows;
    @TableField("grid_columns")
    private int gridColumns;
}
