package com.company.meetinghelper.participant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ParticipantMapper extends BaseMapper<ParticipantEntity> {
    @Select("select * from t_participants where id = #{id}")
    ParticipantEntity selectIncludingDeletedById(@Param("id") String id);

    @Select("""
            select *
            from t_participants
            where meeting_id = #{meetingId}
            order by deleted asc, name asc
            """)
    List<ParticipantEntity> selectAllIncludingDeletedByMeetingId(@Param("meetingId") String meetingId);

    @Update("""
            update t_participants
            set meeting_id = #{meetingId},
                employee_no = #{employeeNo},
                name = #{name},
                attendance_status = #{attendanceStatus},
                locked = #{locked},
                updated_by_id = #{updatedById},
                updated_by_name = #{updatedByName},
                updated_at = #{updatedAt},
                deleted = #{deleted},
                row_version = #{rowVersion}
            where id = #{id}
            """)
    int updateIncludingDeleted(ParticipantEntity participant);
}
