package com.company.meetinghelper.participant.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.participant.entity.MeetingParticipantFieldEntity;
import com.company.meetinghelper.participant.mapper.MeetingParticipantFieldMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MeetingParticipantFieldRepository
        extends AbstractMyBatisRepository<MeetingParticipantFieldEntity> {
    private final MeetingParticipantFieldMapper fieldMapper;

    public MeetingParticipantFieldRepository(MeetingParticipantFieldMapper fieldMapper) {
        super(fieldMapper);
        this.fieldMapper = fieldMapper;
    }

    /**
     * 查询会议中全部有效的人员字段定义。
     *
     * @param meetingId 会议ID
     * @return 按字段排序号升序排列的字段定义
     */
    public List<MeetingParticipantFieldEntity> findAllByMeetingIdOrderBySortOrderAsc(
            String meetingId
    ) {
        return fieldMapper.selectList(new LambdaQueryWrapper<MeetingParticipantFieldEntity>()
                .eq(MeetingParticipantFieldEntity::getMeetingId, meetingId)
                .orderByAsc(MeetingParticipantFieldEntity::getSortOrder));
    }

    /**
     * 根据会议和字段名称查询有效字段定义。
     *
     * @param meetingId 会议ID
     * @param fieldName 字段名称
     * @return 字段定义
     */
    public Optional<MeetingParticipantFieldEntity> findByMeetingIdAndFieldNameIgnoreCase(
            String meetingId,
            String fieldName
    ) {
        return Optional.ofNullable(fieldMapper.selectOne(
                new LambdaQueryWrapper<MeetingParticipantFieldEntity>()
                        .eq(MeetingParticipantFieldEntity::getMeetingId, meetingId)
                        .apply("lower(field_name) = lower({0})", fieldName)
                        .last("limit 1")));
    }

    /**
     * 物理删除字段定义，以允许版本恢复后重新使用相同字段名。
     *
     * @param fields 待删除字段定义
     */
    @Override
    public void deleteAll(Collection<MeetingParticipantFieldEntity> fields) {
        for (MeetingParticipantFieldEntity field : fields) {
            fieldMapper.physicalDeleteById(field.getId());
        }
    }
}
