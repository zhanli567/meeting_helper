package com.company.meetinghelper.meeting.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.meetinghelper.common.entity.LayoutElementEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MeetingElementEntity 类。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("t_meeting_elements")
public class MeetingElementEntity extends LayoutElementEntity {
    @TableField("meeting_id")
    private String meetingId;

    @TableField("source_element_id")
    private String sourceElementId;
}
