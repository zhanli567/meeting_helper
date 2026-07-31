package com.company.meetinghelper.participant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.participant.entity.ParticipantRecordEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * ParticipantRecordMapper 接口。
 */
public interface ParticipantRecordMapper extends BaseMapper<ParticipantRecordEntity> {
    @Delete("delete from t_participant_records where id = #{id}")
    int physicalDeleteById(@Param("id") String id);
}
