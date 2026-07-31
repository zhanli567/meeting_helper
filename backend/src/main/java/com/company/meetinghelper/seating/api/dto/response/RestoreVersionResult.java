package com.company.meetinghelper.seating.api.dto.response;

/**
 * Represents the restore version result record.
 *
 * @param id id
 * @param versionNo version no
 * @param versionName version name
 * @param restoredItems restored items
 */
public record RestoreVersionResult(
        String id,
        int versionNo,
        String versionName,
        int restoredItems
) {
}
