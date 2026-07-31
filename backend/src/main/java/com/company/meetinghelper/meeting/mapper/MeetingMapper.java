package com.company.meetinghelper.meeting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MeetingMapper 接口。
 */
public interface MeetingMapper extends BaseMapper<MeetingEntity> {
    @Select("""
            select *
            from t_meetings
            where id = #{meetingId}
            for update
            """)
    MeetingEntity selectByIdForUpdate(@Param("meetingId") String meetingId);

    @Update("""
            update t_meetings
            set venue_template_id = null
            where venue_template_id = #{venueTemplateId}
            """)
    int clearVenueTemplateId(@Param("venueTemplateId") String venueTemplateId);
}
