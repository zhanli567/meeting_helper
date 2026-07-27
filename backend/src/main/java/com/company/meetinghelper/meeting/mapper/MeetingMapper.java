package com.company.meetinghelper.meeting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MeetingMapper extends BaseMapper<MeetingEntity> {
    @Select("""
            select *
            from t_meetings
            where id = #{meetingId}
            for update
            """)
    MeetingEntity selectByIdForUpdate(@Param("meetingId") String meetingId);
}
