package com.company.meetinghelper.venue.api.dto.request;

import com.company.meetinghelper.venue.api.dto.ElementInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateVenueRequest(
        @NotBlank String name,
        String description,
        @Min(1) int gridRows,
        @Min(1) int gridColumns,
        @Min(20) int cellSize,
        @NotBlank String frontDirection,
        @NotEmpty List<@Valid ElementInput> elements
) {
}
