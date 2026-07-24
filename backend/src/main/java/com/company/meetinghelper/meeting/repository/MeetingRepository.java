package com.company.meetinghelper.meeting.repository;

import com.company.meetinghelper.meeting.entity.MeetingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRepository extends JpaRepository<MeetingEntity, String> {
    List<MeetingEntity> findAllByDeletedFalseOrderByUpdatedAtDesc();

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);
}
