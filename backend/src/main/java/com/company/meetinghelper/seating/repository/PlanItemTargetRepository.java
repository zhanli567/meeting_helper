package com.company.meetinghelper.seating.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.seating.entity.PlanItemTargetEntity;
import com.company.meetinghelper.seating.mapper.PlanItemTargetMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PlanItemTargetRepository extends AbstractMyBatisRepository<PlanItemTargetEntity> {
    private final PlanItemTargetMapper targetMapper;

    public PlanItemTargetRepository(PlanItemTargetMapper targetMapper) {
        super(targetMapper);
        this.targetMapper = targetMapper;
    }

    /**
     * 根据排座明细ID集合查询有效目标位置。
     *
     * @param planItemIds 排座明细ID集合
     * @return 目标位置列表
     */
    public List<PlanItemTargetEntity> findAllByPlanItemIdInAndDeletedFalse(Collection<String> planItemIds) {
        if (planItemIds.isEmpty()) {
            return List.of();
        }
        return targetMapper.selectList(new LambdaQueryWrapper<PlanItemTargetEntity>()
                .in(PlanItemTargetEntity::getPlanItemId, planItemIds));
    }

    /**
     * 根据会议元素ID查询有效目标位置。
     *
     * @param meetingElementId 会议元素ID
     * @return 目标位置
     */
    public Optional<PlanItemTargetEntity> findByMeetingElementIdAndDeletedFalse(String meetingElementId) {
        return Optional.ofNullable(targetMapper.selectOne(new LambdaQueryWrapper<PlanItemTargetEntity>()
                .eq(PlanItemTargetEntity::getMeetingElementId, meetingElementId)
                .last("limit 1")));
    }

    /**
     * 物理删除指定排座明细的全部目标位置。
     *
     * @param planItemId 排座明细ID
     */
    public void deleteAllByPlanItemId(String planItemId) {
        targetMapper.physicalDeleteByPlanItemId(planItemId);
    }

    /**
     * 物理删除目标位置，以便同一座位立即重新分配。
     *
     * @param target 待删除目标位置
     */
    @Override
    public void delete(PlanItemTargetEntity target) {
        targetMapper.physicalDeleteById(target.getId());
    }

    /**
     * 批量物理删除目标位置。
     *
     * @param targets 待删除目标位置
     */
    @Override
    public void deleteAll(Collection<PlanItemTargetEntity> targets) {
        for (PlanItemTargetEntity target : targets) {
            targetMapper.physicalDeleteById(target.getId());
        }
    }
}
