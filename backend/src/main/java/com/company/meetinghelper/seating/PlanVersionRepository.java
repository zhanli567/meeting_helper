package com.company.meetinghelper.seating;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanVersionRepository extends JpaRepository<PlanVersionEntity, String> {
    List<PlanVersionEntity> findAllByPlanIdAndDeletedFalseOrderByVersionNoDesc(String planId);
}

