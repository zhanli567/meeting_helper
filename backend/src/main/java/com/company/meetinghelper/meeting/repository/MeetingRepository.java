package com.company.meetinghelper.meeting.repository;

import com.company.meetinghelper.meeting.entity.MeetingEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<MeetingEntity, String> {
    /**
     * 查询指定创建人的全部有效会议。
     *
     * @param createdById 创建人ID
     * @return 按更新时间倒序排列的会议
     */
    List<MeetingEntity> findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(String createdById);

    /**
     * 按ID和创建人查询有效会议。
     *
     * @param id 会议ID
     * @param createdById 创建人ID
     * @return 匹配的有效会议
     */
    Optional<MeetingEntity> findByIdAndCreatedByIdAndDeletedFalse(String id, String createdById);

    /**
     * 在当前事务内锁定有效会议行，串行化该会议的动态字段读取与注册。
     *
     * @param meetingId 会议ID
     * @return 已加悲观写锁的会议
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select meeting
            from MeetingEntity meeting
            where meeting.id = :meetingId
              and meeting.deleted = false
            """)
    Optional<MeetingEntity> findByIdAndDeletedFalseForUpdate(
            @Param("meetingId") String meetingId
    );

    /**
     * 判断指定创建人的有效会议名称是否已存在。
     *
     * @param createdById 创建人ID
     * @param name 会议名称
     * @return 名称已存在时返回true
     */
    boolean existsByCreatedByIdAndNameIgnoreCaseAndDeletedFalse(String createdById, String name);
}
