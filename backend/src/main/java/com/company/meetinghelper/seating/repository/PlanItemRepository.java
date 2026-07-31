package com.company.meetinghelper.seating.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.seating.entity.PlanItemEntity;
import com.company.meetinghelper.seating.entity.PlanItemType;
import com.company.meetinghelper.seating.mapper.PlanItemMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Represents the plan item repository class.
 */
@Repository
public class PlanItemRepository extends AbstractMyBatisRepository<PlanItemEntity> {
    private final PlanItemMapper itemMapper;

    public PlanItemRepository(PlanItemMapper itemMapper) {
        super(itemMapper);
        this.itemMapper = itemMapper;
    }

    /**
     * 查询排座方案下全部有效明细。
     *
     * @param planId 排座方案ID
     * @return 按创建时间升序排列的排座明细
     */
    public List<PlanItemEntity> findAllByPlanIdOrderByCreatedAtAsc(String planId) {
        return itemMapper.selectList(new LambdaQueryWrapper<PlanItemEntity>()
                .eq(PlanItemEntity::getPlanId, planId)
                .orderByAsc(PlanItemEntity::getCreatedAt));
    }

    /**
     * 查询指定人员和类型的有效排座明细。
     *
     * @param planId 排座方案ID
     * @param participantId 参会人员ID
     * @param itemType 排座明细类型
     * @return 排座明细
     */
    public Optional<PlanItemEntity> findByPlanIdAndParticipantIdAndItemType(
            String planId,
            String participantId,
            PlanItemType itemType
    ) {
        return Optional.ofNullable(itemMapper.selectOne(new LambdaQueryWrapper<PlanItemEntity>()
                .eq(PlanItemEntity::getPlanId, planId)
                .eq(PlanItemEntity::getParticipantId, participantId)
                .eq(PlanItemEntity::getItemType, itemType)
                .last("limit 1")));
    }

    /**
     * 物理删除排座明细，避免恢复版本时遗留无效明细。
     *
     * @param item 待删除排座明细
     */
    @Override
    public void delete(PlanItemEntity item) {
        itemMapper.physicalDeleteById(item.getId());
    }

    /**
     * 批量物理删除排座明细。
     *
     * @param items 待删除排座明细
     */
    @Override
    public void deleteAll(Collection<PlanItemEntity> items) {
        for (PlanItemEntity item : items) {
            itemMapper.physicalDeleteById(item.getId());
        }
    }
}
