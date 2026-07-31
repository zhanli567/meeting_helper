package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Represents the create version request record.
 *
 * @param versionName version name
 * @param changeNote change note
 * @param automatic automatic
 */
public record CreateVersionRequest(
        @NotBlank @Size(max = 120) String versionName,
        @Size(max = 500) String changeNote,
        boolean automatic
) {
}
