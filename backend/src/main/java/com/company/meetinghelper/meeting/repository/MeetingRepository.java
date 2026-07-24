package com.company.meetinghelper.meeting.repository;

import com.company.meetinghelper.meeting.entity.MeetingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRepository extends JpaRepository<MeetingEntity, String> {
    /**
     * 查询全部有效会议。
     *
     * @return 按更新时间倒序排列的会议
     */
    List<MeetingEntity> findAllByDeletedFalseOrderByUpdatedAtDesc();

    /**
     * 判断有效会议名称是否已存在。
     *
     * @param name 会议名称
     * @return 名称已存在时返回true
     */
    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);
}
