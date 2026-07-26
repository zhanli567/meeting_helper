package com.company.meetinghelper.seating.repository;

import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SeatingPlanRepository extends JpaRepository<SeatingPlanEntity, String> {
    /**
     * 按方案ID和会议创建人查询有效排座方案。
     *
     * @param planId 排座方案ID
     * @param ownerId 会议创建人ID
     * @return 当前创建人拥有的会议排座方案
     */
    @Query("""
            select plan
            from SeatingPlanEntity plan, MeetingEntity meeting
            where plan.meetingId = meeting.id
              and plan.id = :planId
              and plan.deleted = false
              and meeting.createdById = :ownerId
              and meeting.deleted = false
            """)
    Optional<SeatingPlanEntity> findOwnedById(
            @Param("planId") String planId,
            @Param("ownerId") String ownerId
    );

    /**
     * 查询会议最早创建的有效排座方案。
     *
     * @param meetingId 会议ID
     * @return 排座方案，不存在时返回空
     */
    Optional<SeatingPlanEntity> findFirstByMeetingIdAndDeletedFalseOrderByCreatedAtAsc(String meetingId);
}
