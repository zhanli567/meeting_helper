package com.company.meetinghelper.participant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.participant.entity.MeetingParticipantFieldEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * Represents the meeting participant field mapper interface.
 */
public interface MeetingParticipantFieldMapper extends BaseMapper<MeetingParticipantFieldEntity> {
    @Delete("delete from t_meeting_participant_fields where id = #{id}")
    int physicalDeleteById(@Param("id") String id);
}
