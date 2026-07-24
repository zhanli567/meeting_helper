package com.company.meetinghelper.seating.repository;

import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeatingPlanRepository extends JpaRepository<SeatingPlanEntity, String> {
    /**
     * 查询会议最早创建的有效排座方案。
     *
     * @param meetingId 会议ID
     * @return 排座方案，不存在时返回空
     */
    Optional<SeatingPlanEntity> findFirstByMeetingIdAndDeletedFalseOrderByCreatedAtAsc(String meetingId);
}
