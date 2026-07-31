package com.company.meetinghelper.meeting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * Represents the meeting element mapper interface.
 */
public interface MeetingElementMapper extends BaseMapper<MeetingElementEntity> {
    @Delete("delete from t_meeting_elements where id = #{id}")
    int physicalDeleteById(@Param("id") String id);
}
