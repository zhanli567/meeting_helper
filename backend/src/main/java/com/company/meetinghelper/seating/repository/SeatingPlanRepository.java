package com.company.meetinghelper.seating.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import com.company.meetinghelper.seating.mapper.SeatingPlanMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Represents the seating plan repository class.
 */
@Repository
public class SeatingPlanRepository extends AbstractMyBatisRepository<SeatingPlanEntity> {
    private final SeatingPlanMapper planMapper;

    public SeatingPlanRepository(SeatingPlanMapper planMapper) {
        super(planMapper);
        this.planMapper = planMapper;
    }

    /**
     * 按方案ID和会议创建人查询有效排座方案。
     *
     * @param planId 方案ID
     * @param ownerId 会议创建人ID
     * @return 当前创建人拥有的会议排座方案
     */
    public Optional<SeatingPlanEntity> findOwnedById(String planId, String ownerId) {
        return Optional.ofNullable(planMapper.selectOwnedById(planId, ownerId));
    }

    /**
     * 查询会议最早创建的有效排座方案。
     *
     * @param meetingId 会议ID
     * @return 排座方案
     */
    public Optional<SeatingPlanEntity> findFirstByMeetingIdOrderByCreatedAtAsc(String meetingId) {
        return Optional.ofNullable(planMapper.selectOne(new LambdaQueryWrapper<SeatingPlanEntity>()
                .eq(SeatingPlanEntity::getMeetingId, meetingId)
                .orderByAsc(SeatingPlanEntity::getCreatedAt)
                .last("limit 1")));
    }

    /**
     * 查询会议下的全部有效排座方案。
     *
     * @param meetingId 会议ID
     * @return 排座方案列表
     */
    public List<SeatingPlanEntity> findAllByMeetingIdOrderByCreatedAtAsc(String meetingId) {
        return planMapper.selectList(new LambdaQueryWrapper<SeatingPlanEntity>()
                .eq(SeatingPlanEntity::getMeetingId, meetingId)
                .orderByAsc(SeatingPlanEntity::getCreatedAt));
    }
}
