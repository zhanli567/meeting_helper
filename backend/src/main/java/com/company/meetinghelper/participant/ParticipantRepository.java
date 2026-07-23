package com.company.meetinghelper.participant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<ParticipantEntity, String> {
    List<ParticipantEntity> findAllByMeetingIdAndDeletedFalseOrderByNameAsc(String meetingId);
    Optional<ParticipantEntity> findByMeetingIdAndEmployeeNoAndDeletedFalse(String meetingId, String employeeNo);
    long countByMeetingIdAndDeletedFalse(String meetingId);
}

