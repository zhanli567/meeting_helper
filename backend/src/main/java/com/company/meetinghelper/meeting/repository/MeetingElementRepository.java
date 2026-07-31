package com.company.meetinghelper.meeting.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.meetinghelper.common.repository.AbstractMyBatisRepository;
import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import com.company.meetinghelper.meeting.mapper.MeetingElementMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Represents the meeting element repository class.
 */
@Repository
public class MeetingElementRepository extends AbstractMyBatisRepository<MeetingElementEntity> {
    private final MeetingElementMapper elementMapper;

    public MeetingElementRepository(MeetingElementMapper elementMapper) {
        super(elementMapper);
        this.elementMapper = elementMapper;
    }

    /**
     * 查询会议快照中的全部有效元素。
     *
     * @param meetingId 会议ID
     * @return 按网格位置排列的会议元素
     */
    public List<MeetingElementEntity> findAllByMeetingIdOrderByStartRowAscStartColumnAsc(
            String meetingId
    ) {
        return elementMapper.selectList(new LambdaQueryWrapper<MeetingElementEntity>()
                .eq(MeetingElementEntity::getMeetingId, meetingId)
                .orderByAsc(MeetingElementEntity::getStartRow)
                .orderByAsc(MeetingElementEntity::getStartColumn));
    }

    /**
     * 按元素ID和会议ID查询有效会议元素。
     *
     * @param id 会议元素ID
     * @param meetingId 会议ID
     * @return 属于指定会议的有效会议元素
     */
    public Optional<MeetingElementEntity> findByIdAndMeetingId(String id, String meetingId) {
        return Optional.ofNullable(elementMapper.selectOne(new LambdaQueryWrapper<MeetingElementEntity>()
                .eq(MeetingElementEntity::getId, id)
                .eq(MeetingElementEntity::getMeetingId, meetingId)
                .last("limit 1")));
    }

    /**
     * 物理删除会议快照中的全部元素。
     *
     * @param meetingId 会议ID
     */
    public void deleteAllByMeetingId(String meetingId) {
        deleteAll(findAllByMeetingIdOrderByStartRowAscStartColumnAsc(meetingId));
    }

    /**
     * 物理删除会议元素，避免重新保存布局时被软删除记录占位。
     *
     * @param elements 待删除元素
     */
    @Override
    public void deleteAll(Collection<MeetingElementEntity> elements) {
        for (MeetingElementEntity element : elements) {
            elementMapper.physicalDeleteById(element.getId());
        }
    }
}
