package com.company.meetinghelper.award.repository;

import com.company.meetinghelper.award.entity.AwardRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AwardRecordRepository extends JpaRepository<AwardRecordEntity, String> {
    /**
     * 查询指定人员的全部有效获奖记录。
     *
     * @param participantIds 参会人员ID集合
     * @return 按颁奖批次升序排列的获奖记录
     */
    List<AwardRecordEntity> findAllByParticipantIdInAndDeletedFalseOrderByBatchOrderAsc(Collection<String> participantIds);

    /**
     * 删除指定人员的全部获奖记录。
     *
     * @param participantId 参会人员ID
     */
    void deleteAllByParticipantId(String participantId);
}
