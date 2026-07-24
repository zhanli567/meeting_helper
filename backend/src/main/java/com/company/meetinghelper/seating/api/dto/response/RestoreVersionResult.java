package com.company.meetinghelper.seating.api.dto.response;

public record RestoreVersionResult(
        String id,
        int versionNo,
        String versionName,
        int restoredItems
) {
}
