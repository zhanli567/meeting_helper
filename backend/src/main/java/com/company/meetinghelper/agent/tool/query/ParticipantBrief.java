package com.company.meetinghelper.agent.tool.query;

import java.util.Map;

/**
 * 智能体可读的人员摘要。
 *
 * @param id 人员ID
 * @param employeeNo 工号
 * @param name 姓名
 * @param attendanceStatus 出席状态
 * @param locked 是否锁定
 * @param assignedElementId 已分配座位ID
 * @param attributes 主动态字段
 */
public record ParticipantBrief(
        String id,
        String employeeNo,
        String name,
        String attendanceStatus,
        boolean locked,
        String assignedElementId,
        Map<String, String> attributes
) {
}
