package com.company.meetinghelper.seating.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.seating.entity.PlanItemTargetEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * PlanItemTargetMapper 接口。
 */
public interface PlanItemTargetMapper extends BaseMapper<PlanItemTargetEntity> {
    @Delete("delete from t_plan_item_targets where id = #{id}")
    int physicalDeleteById(@Param("id") String id);

    @Delete("delete from t_plan_item_targets where plan_item_id = #{planItemId}")
    int physicalDeleteByPlanItemId(@Param("planItemId") String planItemId);
}
