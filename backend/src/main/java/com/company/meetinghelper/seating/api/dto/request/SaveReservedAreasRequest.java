package com.company.meetinghelper.seating.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * SaveReservedAreasRequest 数据结构。
 * @param reservedAreas reservedAreas 参数。
 */
public record SaveReservedAreasRequest(
        @NotNull List<@Valid ReservedAreaInput> reservedAreas
) {
}
