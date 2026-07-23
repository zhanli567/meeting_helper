package com.company.meetinghelper.seating;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanVersionRepository extends JpaRepository<PlanVersionEntity, String> {
    List<PlanVersionEntity> findAllByPlanIdAndDeletedFalseOrderByVersionNoDesc(String planId);
    Optional<PlanVersionEntity> findFirstByPlanIdAndDeletedFalseOrderByVersionNoDesc(String planId);
}
