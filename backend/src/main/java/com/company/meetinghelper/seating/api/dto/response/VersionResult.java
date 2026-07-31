package com.company.meetinghelper.seating.api.dto.response;

/**
 * Represents the version result record.
 *
 * @param id id
 * @param versionNo version no
 * @param versionName version name
 * @param assignedCount assigned count
 * @param unassignedCount unassigned count
 */
public record VersionResult(
        String id,
        int versionNo,
        String versionName,
        int assignedCount,
        int unassignedCount
) {
}
