package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateVersionRequest(
        @NotBlank String versionName,
        String changeNote,
        boolean automatic
) {
}
