package com.company.meetinghelper.meeting.repository;

import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingElementRepository extends JpaRepository<MeetingElementEntity, String> {
    /**
     * 查询会议快照中的全部有效元素。
     *
     * @param meetingId 会议ID
     * @return 按网格位置排列的会议元素
     */
    List<MeetingElementEntity> findAllByMeetingIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(String meetingId);
}
