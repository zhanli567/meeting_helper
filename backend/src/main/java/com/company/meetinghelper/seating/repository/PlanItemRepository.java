package com.company.meetinghelper.seating.repository;

import com.company.meetinghelper.seating.entity.PlanItemEntity;
import com.company.meetinghelper.seating.entity.PlanItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanItemRepository extends JpaRepository<PlanItemEntity, String> {
    List<PlanItemEntity> findAllByPlanIdAndDeletedFalseOrderByCreatedAtAsc(String planId);
    Optional<PlanItemEntity> findByPlanIdAndParticipantIdAndItemTypeAndDeletedFalse(
            String planId, String participantId, PlanItemType itemType);
}
