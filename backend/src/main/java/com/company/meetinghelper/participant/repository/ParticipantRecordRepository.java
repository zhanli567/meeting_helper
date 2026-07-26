package com.company.meetinghelper.participant.repository;

import com.company.meetinghelper.participant.entity.ParticipantRecordEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRecordRepository extends JpaRepository<ParticipantRecordEntity, String> {
    /**
     * 查询指定人员的全部有效扩展记录。
     *
     * @param participantIds 参会人员ID集合
     * @return 先按人员ID、再按记录排序号升序排列的扩展记录
     */
    List<ParticipantRecordEntity> findAllByParticipantIdInAndDeletedFalseOrderByParticipantIdAscRecordOrderAsc(
            Collection<String> participantIds
    );

    /**
     * 查询指定人员的全部有效扩展记录。
     *
     * @param participantId 参会人员ID
     * @return 按记录排序号升序排列的扩展记录
     */
    List<ParticipantRecordEntity> findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(String participantId);
}
