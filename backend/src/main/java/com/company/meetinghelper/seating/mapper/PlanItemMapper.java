package com.company.meetinghelper.seating.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.meetinghelper.seating.entity.PlanItemEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface PlanItemMapper extends BaseMapper<PlanItemEntity> {
    @Delete("delete from t_plan_items where id = #{id}")
    int physicalDeleteById(@Param("id") String id);
}
