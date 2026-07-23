package com.company.meetinghelper.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRepository extends JpaRepository<MeetingEntity, String> {
    List<MeetingEntity> findAllByDeletedFalseOrderByUpdatedAtDesc();
}

