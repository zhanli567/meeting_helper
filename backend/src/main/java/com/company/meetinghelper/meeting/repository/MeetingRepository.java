package com.company.meetinghelper.meeting.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import com.company.meetinghelper.meeting.mapper.MeetingMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MeetingRepository extends AbstractMyBatisRepository<MeetingEntity> {
    private final MeetingMapper meetingMapper;

    public MeetingRepository(MeetingMapper meetingMapper) {
        super(meetingMapper);
        this.meetingMapper = meetingMapper;
    }

    /**
     * 查询指定创建人的全部有效会议。
     *
     * @param createdById 创建人ID
     * @return 按更新时间倒序排列的会议
     */
    public List<MeetingEntity> findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(String createdById) {
        return meetingMapper.selectList(new LambdaQueryWrapper<MeetingEntity>()
                .eq(MeetingEntity::getCreatedById, createdById)
                .orderByDesc(MeetingEntity::getUpdatedAt));
    }

    /**
     * 按ID和创建人查询有效会议。
     *
     * @param id 会议ID
     * @param createdById 创建人ID
     * @return 匹配的有效会议
     */
    public Optional<MeetingEntity> findByIdAndCreatedByIdAndDeletedFalse(String id, String createdById) {
        return Optional.ofNullable(meetingMapper.selectOne(new LambdaQueryWrapper<MeetingEntity>()
                .eq(MeetingEntity::getId, id)
                .eq(MeetingEntity::getCreatedById, createdById)
                .last("limit 1")));
    }

    /**
     * 在当前事务内锁定有效会议行。
     *
     * @param meetingId 会议ID
     * @return 已加悲观写锁的会议
     */
    public Optional<MeetingEntity> findByIdAndDeletedFalseForUpdate(String meetingId) {
        return Optional.ofNullable(meetingMapper.selectByIdForUpdate(meetingId));
    }

    /**
     * 判断指定创建人的有效会议名称是否已存在。
     *
     * @param createdById 创建人ID
     * @param name 会议名称
     * @return 名称已存在时返回true
     */
    public boolean existsByCreatedByIdAndNameIgnoreCaseAndDeletedFalse(String createdById, String name) {
        return meetingMapper.selectCount(new LambdaQueryWrapper<MeetingEntity>()
                .eq(MeetingEntity::getCreatedById, createdById)
                .apply("lower(name) = lower({0})", name)) > 0;
    }
}
