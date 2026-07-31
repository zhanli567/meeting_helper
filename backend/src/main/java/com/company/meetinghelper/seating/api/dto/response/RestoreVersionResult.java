package com.company.meetinghelper.seating.api.dto.response;

/**
 * RestoreVersionResult 数据结构。
 * @param id id 参数。
 * @param versionNo versionNo 参数。
 * @param versionName versionName 参数。
 * @param restoredItems restoredItems 参数。
 */
public record RestoreVersionResult(
        String id,
        int versionNo,
        String versionName,
        int restoredItems
) {
}
