package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * ReservedAreaInput 数据结构。
 * @param id id 参数。
 * @param label label 参数。
 * @param backgroundColor backgroundColor 参数。
 * @param textColor textColor 参数。
 * @param bold bold 参数。
 * @param targetElementIds targetElementIds 参数。
 */
public record ReservedAreaInput(
        String id,
        @NotBlank String label,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String backgroundColor,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String textColor,
        boolean bold,
        @NotEmpty List<@NotBlank String> targetElementIds
) {
}
