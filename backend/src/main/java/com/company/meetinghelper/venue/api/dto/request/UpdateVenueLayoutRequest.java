package com.company.meetinghelper.venue.api.dto.request;

import com.company.meetinghelper.venue.api.dto.ElementInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * UpdateVenueLayoutRequest 数据结构。
 * @param gridRows gridRows 参数。
 * @param gridColumns gridColumns 参数。
 * @param elements elements 参数。
 * @param rowVersion rowVersion 参数。
 */
public record UpdateVenueLayoutRequest(
        @Min(5) int gridRows,
        @Min(5) int gridColumns,
        @NotNull List<@Valid ElementInput> elements,
        @PositiveOrZero long rowVersion
) {
}
