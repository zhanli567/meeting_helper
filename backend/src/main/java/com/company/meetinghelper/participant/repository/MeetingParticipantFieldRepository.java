package com.company.meetinghelper.participant.repository;

import com.company.meetinghelper.participant.entity.MeetingParticipantFieldEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingParticipantFieldRepository extends JpaRepository<MeetingParticipantFieldEntity, String> {
    /**
     * 查询会议中全部有效的人员字段定义。
     *
     * @param meetingId 会议ID
     * @return 按字段排序号升序排列的字段定义
     */
    List<MeetingParticipantFieldEntity> findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(String meetingId);

    /**
     * 根据会议和字段名称查询有效的人员字段定义。
     *
     * @param meetingId 会议ID
     * @param fieldName 字段名称
     * @return 字段定义，不存在时返回空
     */
    Optional<MeetingParticipantFieldEntity> findByMeetingIdAndFieldNameIgnoreCaseAndDeletedFalse(
            String meetingId,
            String fieldName
    );
}
