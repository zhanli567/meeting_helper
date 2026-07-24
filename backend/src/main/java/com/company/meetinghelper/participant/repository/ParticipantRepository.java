package com.company.meetinghelper.participant.repository;

import com.company.meetinghelper.participant.entity.ParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<ParticipantEntity, String> {
    /**
     * 查询会议中的全部有效人员。
     *
     * @param meetingId 会议ID
     * @return 按姓名升序排列的参会人员
     */
    List<ParticipantEntity> findAllByMeetingIdAndDeletedFalseOrderByNameAsc(String meetingId);

    /**
     * 根据会议和工号查询有效人员。
     *
     * @param meetingId 会议ID
     * @param employeeNo 工号
     * @return 参会人员，不存在时返回空
     */
    Optional<ParticipantEntity> findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(
            String meetingId,
            String employeeNo
    );

    /**
     * 统计会议有效人员数量。
     *
     * @param meetingId 会议ID
     * @return 有效人员数量
     */
    long countByMeetingIdAndDeletedFalse(String meetingId);
}
