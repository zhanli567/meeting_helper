package com.company.meetinghelper.participant.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.mapper.ParticipantMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ParticipantRepository extends AbstractMyBatisRepository<ParticipantEntity> {
    private final ParticipantMapper participantMapper;

    public ParticipantRepository(ParticipantMapper participantMapper) {
        super(participantMapper);
        this.participantMapper = participantMapper;
    }

    /**
     * 新增或更新人员；更新时允许恢复已逻辑删除的同工号人员。
     *
     * @param participant 待保存人员
     * @return 已保存人员
     */
    /**
     * 按主键查询人员，包括已逻辑删除的人员。
     *
     * @param id 人员ID
     * @return 匹配人员
     */
    /**
     * 查询会议中的全部有效人员。
     *
     * @param meetingId 会议ID
     * @return 按姓名升序排列的参会人员
     */
    public List<ParticipantEntity> findAllByMeetingIdOrderByNameAsc(String meetingId) {
        return participantMapper.selectList(new LambdaQueryWrapper<ParticipantEntity>()
                .eq(ParticipantEntity::getMeetingId, meetingId)
                .orderByAsc(ParticipantEntity::getName));
    }

    /**
     * 查询会议中的全部人员，包括逻辑删除记录。
     *
     * @param meetingId 会议ID
     * @return 有效人员优先、姓名升序排列的参会人员
     */
    /**
     * 按人员ID和会议ID查询有效人员。
     *
     * @param id 人员ID
     * @param meetingId 会议ID
     * @return 属于指定会议的有效人员
     */
    public Optional<ParticipantEntity> findByIdAndMeetingId(String id, String meetingId) {
        return Optional.ofNullable(participantMapper.selectOne(new LambdaQueryWrapper<ParticipantEntity>()
                .eq(ParticipantEntity::getId, id)
                .eq(ParticipantEntity::getMeetingId, meetingId)
                .last("limit 1")));
    }

    /**
     * 根据会议和工号查询有效人员。
     *
     * @param meetingId 会议ID
     * @param employeeNo 工号
     * @return 参会人员
     */
    public Optional<ParticipantEntity> findByMeetingIdAndEmployeeNoIgnoreCase(
            String meetingId,
            String employeeNo
    ) {
        return Optional.ofNullable(participantMapper.selectOne(new LambdaQueryWrapper<ParticipantEntity>()
                .eq(ParticipantEntity::getMeetingId, meetingId)
                .apply("lower(employee_no) = lower({0})", employeeNo)
                .last("limit 1")));
    }

    /**
     * 统计会议有效人员数量。
     *
     * @param meetingId 会议ID
     * @return 有效人员数量
     */
    public long countByMeetingId(String meetingId) {
        return participantMapper.selectCount(new LambdaQueryWrapper<ParticipantEntity>()
                .eq(ParticipantEntity::getMeetingId, meetingId));
    }
}
