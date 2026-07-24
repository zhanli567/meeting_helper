package com.company.meetinghelper.seating.repository;

import com.company.meetinghelper.seating.entity.PlanVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanVersionRepository extends JpaRepository<PlanVersionEntity, String> {
    /**
     * 查询排座方案的全部有效版本。
     *
     * @param planId 排座方案ID
     * @return 按版本号倒序排列的版本
     */
    List<PlanVersionEntity> findAllByPlanIdAndDeletedFalseOrderByVersionNoDesc(String planId);

    /**
     * 查询排座方案最新的有效版本。
     *
     * @param planId 排座方案ID
     * @return 最新版本，不存在时返回空
     */
    Optional<PlanVersionEntity> findFirstByPlanIdAndDeletedFalseOrderByVersionNoDesc(String planId);
}
