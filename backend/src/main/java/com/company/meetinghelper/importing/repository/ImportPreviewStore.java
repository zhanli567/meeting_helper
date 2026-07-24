package com.company.meetinghelper.importing.repository;

import com.company.meetinghelper.importing.api.dto.response.ImportPreview;
import com.company.meetinghelper.importing.service.model.ParsedWorkbook;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     * 读取并移除一次性导入预览。
     *
     * @param token 预览令牌
     * @return 预览数据，不存在时返回null
     */
    public StoredPreview remove(String token) {
        return previews.remove(token);
    }

    public record StoredPreview(
            String meetingId,
            String templateCode,
            ParsedWorkbook workbook,
            ImportPreview preview,
            OffsetDateTime createdAt
    ) {
    }
}
