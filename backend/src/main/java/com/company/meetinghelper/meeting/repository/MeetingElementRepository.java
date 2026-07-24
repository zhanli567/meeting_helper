package com.company.meetinghelper.meeting.repository;

import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingElementRepository extends JpaRepository<MeetingElementEntity, String> {
    List<MeetingElementEntity> findAllByMeetingIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(String meetingId);
}
