package com.company.meetinghelper.seating.repository;

import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeatingPlanRepository extends JpaRepository<SeatingPlanEntity, String> {
    Optional<SeatingPlanEntity> findFirstByMeetingIdAndDeletedFalseOrderByCreatedAtAsc(String meetingId);
}
