package com.company.meetinghelper.venue.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ElementInput(
        @NotBlank String type,
        String code,
        String label,
        @Min(1) int row,
        @Min(1) int column,
        @Min(1) int rowSpan,
        @Min(1) int columnSpan,
        int rotation,
        int capacity,
        boolean assignable,
        boolean walkable,
        String groupCode,
        String groupLabel,
        Integer sequenceNo,
        String backgroundColor,
        String borderColor
) {
}
