package com.company.meetinghelper.venue.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.LayoutElementEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * VenueElementEntity 类。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("t_venue_elements")
public class VenueElementEntity extends LayoutElementEntity {
    @TableField("venue_template_id")
    private String venueTemplateId;
}
