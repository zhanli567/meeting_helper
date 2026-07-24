package com.company.meetinghelper.importing.api.dto.response;

import java.util.List;

public record TemplateDescriptor(
        String code,
        String name,
        String description,
        int version,
        List<SheetDescriptor> sheets
) {
}
