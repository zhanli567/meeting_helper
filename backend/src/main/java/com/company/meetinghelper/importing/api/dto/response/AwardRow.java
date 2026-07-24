package com.company.meetinghelper.importing.api.dto.response;

public record AwardRow(
        int sourceRow,
        String employeeNo,
        int batchOrder,
        String batchName,
        String awardName,
        String awardLevel,
        String projectName,
        Integer teamSize
) {
}
