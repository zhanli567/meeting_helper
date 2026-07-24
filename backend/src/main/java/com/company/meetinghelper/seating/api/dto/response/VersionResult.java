package com.company.meetinghelper.seating.api.dto.response;

public record VersionResult(
        String id,
        int versionNo,
        String versionName,
        int assignedCount,
        int unassignedCount
) {
}
