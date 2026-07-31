package com.company.meetinghelper.venue.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Represents the element input record.
 *
 * @param kind kind
 * @param name name
 * @param row row
 * @param column column
 * @param rowSpan row span
 * @param columnSpan column span
 * @param fillColor fill color
 */
public record ElementInput(
        @NotBlank String kind,
        @NotBlank @Size(max = 80) String name,
        @Min(1) int row,
        @Min(1) int column,
        @Min(1) int rowSpan,
        @Min(1) int columnSpan,
        @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String fillColor
) {
}
