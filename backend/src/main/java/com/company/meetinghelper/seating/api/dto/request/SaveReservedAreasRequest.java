package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Represents the save reserved areas request record.
 *
 * @param reservedAreas reserved areas
 */
public record SaveReservedAreasRequest(
        @NotNull List<@Valid ReservedAreaInput> reservedAreas
) {
}
