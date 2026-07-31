package com.company.meetinghelper.participant.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.participant.entity.ParticipantRecordEntity;
import com.company.meetinghelper.participant.mapper.ParticipantRecordMapper;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * ParticipantRecordRepository 类。
 */
@Repository
public class ParticipantRecordRepository extends AbstractMyBatisRepository<ParticipantRecordEntity> {
    private final ParticipantRecordMapper recordMapper;

    public ParticipantRecordRepository(ParticipantRecordMapper recordMapper) {
        super(recordMapper);
        this.recordMapper = recordMapper;
    }

    /**
     * 查询指定人员集合的全部有效扩展记录。
     *
     * @param participantIds 参会人员ID集合
     * @return 按人员ID和记录排序号升序排列的扩展记录
     */
    public List<ParticipantRecordEntity> findAllByParticipantIdInOrderByParticipantIdAscRecordOrderAsc(
            Collection<String> participantIds
    ) {
        if (participantIds.isEmpty()) {
            return List.of();
        }
        return recordMapper.selectList(new LambdaQueryWrapper<ParticipantRecordEntity>()
                .in(ParticipantRecordEntity::getParticipantId, participantIds)
                .orderByAsc(ParticipantRecordEntity::getParticipantId)
                .orderByAsc(ParticipantRecordEntity::getRecordOrder));
    }

    /**
     * 查询指定人员的全部有效扩展记录。
     *
     * @param participantId 参会人员ID
     * @return 按记录排序号升序排列的扩展记录
     */
    public List<ParticipantRecordEntity> findAllByParticipantIdOrderByRecordOrderAsc(
            String participantId
    ) {
        return recordMapper.selectList(new LambdaQueryWrapper<ParticipantRecordEntity>()
                .eq(ParticipantRecordEntity::getParticipantId, participantId)
                .orderByAsc(ParticipantRecordEntity::getRecordOrder));
    }

    /**
     * 物理删除扩展记录，以允许版本恢复后重新使用相同记录序号。
     *
     * @param records 待删除扩展记录
     */
    @Override
    public void deleteAll(Collection<ParticipantRecordEntity> records) {
        for (ParticipantRecordEntity record : records) {
            recordMapper.physicalDeleteById(record.getId());
        }
    }
}
