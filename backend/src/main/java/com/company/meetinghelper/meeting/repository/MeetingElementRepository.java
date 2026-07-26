package com.company.meetinghelper.meeting.repository;

import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingElementRepository extends JpaRepository<MeetingElementEntity, String> {
    /**
     * 查询会议快照中的全部有效元素。
     *
     * @param meetingId 会议ID
     * @return 按网格位置排列的会议元素
     */
    List<MeetingElementEntity> findAllByMeetingIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(String meetingId);

    /**
     * 按元素ID和会议ID查询有效会议元素。
     *
     * @param id 会议元素ID
     * @param meetingId 会议ID
     * @return 属于指定会议的有效会议元素，不存在时返回空
     */
    Optional<MeetingElementEntity> findByIdAndMeetingIdAndDeletedFalse(String id, String meetingId);
}
