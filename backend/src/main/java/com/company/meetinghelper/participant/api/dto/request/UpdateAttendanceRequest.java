package com.company.meetinghelper.participant.api.dto.request;

import com.company.meetinghelper.participant.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

/**
 * UpdateAttendanceRequest 数据结构。
 * @param attendanceStatus attendanceStatus 参数。
 */
public record UpdateAttendanceRequest(@NotNull AttendanceStatus attendanceStatus) {
}
