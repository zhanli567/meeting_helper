package com.company.meetinghelper.participant.api.dto.request;

import com.company.meetinghelper.participant.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAttendanceRequest(@NotNull AttendanceStatus attendanceStatus) {
}
