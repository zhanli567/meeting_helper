package com.company.meetinghelper.seating.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SeatingPlanMapper extends BaseMapper<SeatingPlanEntity> {
    @Select("""
            select plan.*
            from t_seating_plans plan
            join t_meetings meeting on meeting.id = plan.meeting_id
            where plan.id = #{planId}
              and plan.deleted = false
              and meeting.created_by_id = #{ownerId}
              and meeting.deleted = false
            """)
    SeatingPlanEntity selectOwnedById(
            @Param("planId") String planId,
            @Param("ownerId") String ownerId
    );
}
