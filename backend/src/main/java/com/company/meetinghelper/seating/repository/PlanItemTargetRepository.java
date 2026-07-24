package com.company.meetinghelper.seating.repository;

import com.company.meetinghelper.seating.entity.PlanItemTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlanItemTargetRepository extends JpaRepository<PlanItemTargetEntity, String> {
    List<PlanItemTargetEntity> findAllByPlanItemIdInAndDeletedFalse(Collection<String> planItemIds);
    Optional<PlanItemTargetEntity> findByMeetingElementIdAndDeletedFalse(String meetingElementId);
    void deleteAllByPlanItemId(String planItemId);
}
