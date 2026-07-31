package com.company.meetinghelper.participant.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.participant.entity.ParticipantRecordEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 人员动态记录属性编解码组件。
 */
@Component
public class ParticipantRecordAttributeCodec {

    private final ObjectMapper objectMapper;

    /**
     * 创建人员动态记录属性编解码组件。
     *
     * @param objectMapper JSON 序列化器。
     */
    public ParticipantRecordAttributeCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 把记录实体转换为合并器输入值。
     *
     * @param records 人员动态记录列表。
     * @return 合并器输入值列表。
     */
    public List<ParticipantRecordMerger.RecordValue> mergerValues(List<ParticipantRecordEntity> records) {
        return records.stream()
                .map(record -> new ParticipantRecordMerger.RecordValue(
                        record.getId(),
                        record.getRecordOrder(),
                        read(record.getAttributesJson())
                ))
                .toList();
    }

    /**
     * 从 JSON 读取人员动态属性。
     *
     * @param json 属性 JSON。
     * @return 人员动态属性。
     */
    public Map<String, String> read(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "人员动态记录格式不正确");
        }
    }

    /**
     * 把人员动态属性写成 JSON。
     *
     * @param attributes 人员动态属性。
     * @return 属性 JSON。
     */
    public String write(Map<String, String> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "人员动态记录无法保存");
        }
    }
}
