package com.company.meetinghelper.seating.api.dto.response;

/**
 * VersionResult 数据结构。
 * @param id id 参数。
 * @param versionNo versionNo 参数。
 * @param versionName versionName 参数。
 * @param assignedCount assignedCount 参数。
 * @param unassignedCount unassignedCount 参数。
 */
public record VersionResult(
        String id,
        int versionNo,
        String versionName,
        int assignedCount,
        int unassignedCount
) {
}
