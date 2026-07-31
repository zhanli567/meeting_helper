package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * CreateVersionRequest 数据结构。
 * @param versionName versionName 参数。
 * @param changeNote changeNote 参数。
 * @param automatic automatic 参数。
 */
public record CreateVersionRequest(
        @NotBlank @Size(max = 120) String versionName,
        @Size(max = 500) String changeNote,
        boolean automatic
) {
}
