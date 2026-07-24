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

    public void put(String token, StoredPreview preview) {
        previews.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(2)));
        previews.put(token, preview);
    }

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
