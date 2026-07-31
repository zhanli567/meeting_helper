package com.company.meetinghelper.venue.api.dto.request;

import com.company.meetinghelper.venue.api.dto.ElementInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * Represents the update venue layout request record.
 *
 * @param gridRows grid rows
 * @param gridColumns grid columns
 * @param elements elements
 * @param rowVersion row version
 */
public record UpdateVenueLayoutRequest(
        @Min(5) int gridRows,
        @Min(5) int gridColumns,
        @NotNull List<@Valid ElementInput> elements,
        @PositiveOrZero long rowVersion
) {
}
