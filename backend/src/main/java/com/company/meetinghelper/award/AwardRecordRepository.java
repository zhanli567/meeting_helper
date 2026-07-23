package com.company.meetinghelper.award;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AwardRecordRepository extends JpaRepository<AwardRecordEntity, String> {
    List<AwardRecordEntity> findAllByParticipantIdInAndDeletedFalseOrderByBatchOrderAsc(Collection<String> participantIds);
    void deleteAllByParticipantId(String participantId);
}

