package com.company.meetinghelper.participant.api.dto.request;

import com.company.meetinghelper.participant.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Represents the update attendance request record.
 *
 * @param attendanceStatus attendance status
 */
public record UpdateAttendanceRequest(@NotNull AttendanceStatus attendanceStatus) {
}
