package com.company.meetinghelper.participant.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.participant.entity.MeetingParticipantFieldEntity;
import com.company.meetinghelper.participant.repository.MeetingParticipantFieldRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParticipantFieldRegistrationService {
    private final MeetingRepository meetingRepository;
    private final MeetingParticipantFieldRepository fieldRepository;

    /**
     * 创建会议人员动态字段注册服务。
     *
     * @param meetingRepository 会议仓储
     * @param fieldRepository 人员动态字段仓储
     */
    public ParticipantFieldRegistrationService(
            MeetingRepository meetingRepository,
            MeetingParticipantFieldRepository fieldRepository
    ) {
        this.meetingRepository = meetingRepository;
        this.fieldRepository = fieldRepository;
    }

    /**
     * 在调用方事务中锁定会议。
     *
     * @param meetingId 会议ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockMeeting(String meetingId) {
        meetingRepository.findByIdAndDeletedFalseForUpdate(meetingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议不存在"));
    }

    /**
     * 在调用方事务中先锁定会议再注册字段，并返回按规范化名称索引的标准字段名。
     *
     * @param meetingId 会议ID
     * @param fieldNames 待注册字段名，迭代顺序决定新增字段顺序
     * @return 规范化字段名到标准字段名的只读映射
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, String> registerFields(
            String meetingId,
            Collection<String> fieldNames
    ) {
        lockMeeting(meetingId);

        List<MeetingParticipantFieldEntity> existing = fieldRepository
                .findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId);
        LinkedHashMap<String,String> canonicalNames = new LinkedHashMap<String, String>();
        existing.forEach(field -> canonicalNames.putIfAbsent(
                normalize(field.getFieldName()),
                field.getFieldName()
        ));
        int nextSortOrder = existing.stream()
                .mapToInt(MeetingParticipantFieldEntity::getSortOrder)
                .max()
                .orElse(0);
        Collection<String> requestedNames = fieldNames == null ? List.<String>of() : fieldNames;
        for (String requestedName : requestedNames) {
            String fieldName = requestedName == null ? "" : requestedName.trim();
            if (fieldName.isBlank()) {
                continue;
            }
            String normalizedName = normalize(fieldName);
            if (canonicalNames.containsKey(normalizedName)) {
                continue;
            }
            MeetingParticipantFieldEntity field = new MeetingParticipantFieldEntity();
            field.setMeetingId(meetingId);
            field.setFieldName(fieldName);
            field.setSortOrder(++nextSortOrder);
            fieldRepository.save(field);
            canonicalNames.put(normalizedName, fieldName);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(canonicalNames));
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
