package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * Represents the reserved area input record.
 *
 * @param id id
 * @param label label
 * @param backgroundColor background color
 * @param textColor text color
 * @param bold bold
 * @param targetElementIds target element ids
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
