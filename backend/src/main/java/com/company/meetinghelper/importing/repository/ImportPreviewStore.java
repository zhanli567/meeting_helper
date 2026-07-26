package com.company.meetinghelper.importing.repository;

import com.company.meetinghelper.importing.api.dto.response.ImportPreview;
import com.company.meetinghelper.importing.service.model.ParsedParticipantWorkbook;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ImportPreviewStore {
    private final Map<String, StoredPreview> previews = new ConcurrentHashMap<>();

    /**
     * 保存导入预览并清理两小时前的过期数据。
     *
     * @param token 预览令牌
     * @param preview 预览数据
     */
    public void put(String token, StoredPreview preview) {
        previews.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(2)));
        previews.put(token, preview);
    }

    /**
     * 仅在预览属于指定会议时读取并原子移除一次性导入预览。
     *
     * @param token 预览令牌
     * @param meetingId 会议ID
     * @return 匹配会议的预览数据，不存在或不匹配时返回null
     */
    public StoredPreview remove(String token, String meetingId) {
        StoredPreview preview = previews.get(token);
        if (preview == null || !preview.meetingId().equals(meetingId)) {
            return null;
        }
        return previews.remove(token, preview) ? preview : null;
    }

    /**
     * 保存提交阶段所需的原始解析结果、展示预览和所属会议信息。
     *
     * @param meetingId 会议ID
     * @param workbook 通用人员工作簿解析结果
     * @param preview 展示给用户的预览
     * @param createdAt 预览创建时间
     */
    public record StoredPreview(
            String meetingId,
            ParsedParticipantWorkbook workbook,
            ImportPreview preview,
            OffsetDateTime createdAt
    ) {
    }
}
