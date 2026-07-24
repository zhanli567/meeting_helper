package com.company.meetinghelper.seating.repository;

import com.company.meetinghelper.seating.entity.PlanItemTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlanItemTargetRepository extends JpaRepository<PlanItemTargetEntity, String> {
    /**
     * 根据排座明细ID集合查询未删除的目标位置。
     *
     * @param planItemIds 排座明细ID集合
     * @return 目标位置列表
     */
    List<PlanItemTargetEntity> findAllByPlanItemIdInAndDeletedFalse(Collection<String> planItemIds);

    /**
     * 根据会议元素ID查询未删除的目标位置。
     *
     * @param meetingElementId 会议元素ID
     * @return 目标位置，不存在时返回空
     */
    Optional<PlanItemTargetEntity> findByMeetingElementIdAndDeletedFalse(String meetingElementId);

    /**
     * 删除指定排座明细的全部目标位置。
     *
     * @param planItemId 排座明细ID
     */
    void deleteAllByPlanItemId(String planItemId);
}
