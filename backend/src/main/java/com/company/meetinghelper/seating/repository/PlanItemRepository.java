package com.company.meetinghelper.seating.repository;

import com.company.meetinghelper.seating.entity.PlanItemEntity;
import com.company.meetinghelper.seating.entity.PlanItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanItemRepository extends JpaRepository<PlanItemEntity, String> {
    /**
     * 查询排座方案下全部未删除明细。
     *
     * @param planId 排座方案ID
     * @return 按创建时间升序排列的排座明细
     */
    List<PlanItemEntity> findAllByPlanIdAndDeletedFalseOrderByCreatedAtAsc(String planId);

    /**
     * 查询指定人员和类型的排座明细。
     *
     * @param planId 排座方案ID
     * @param participantId 参会人员ID
     * @param itemType 排座明细类型
     * @return 排座明细，不存在时返回空
     */
    Optional<PlanItemEntity> findByPlanIdAndParticipantIdAndItemTypeAndDeletedFalse(
            String planId, String participantId, PlanItemType itemType);
}
