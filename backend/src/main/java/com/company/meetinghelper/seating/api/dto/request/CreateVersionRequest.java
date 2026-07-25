package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVersionRequest(
        @NotBlank @Size(max = 120) String versionName,
        @Size(max = 500) String changeNote,
        boolean automatic
) {
}
