package com.company.meetinghelper.agent.tool.query;

/**
 * 工作区汇总结果。
 *
 * @param meetingId 会议ID
 * @param meetingName 会议名称
 * @param participantCount 人员总数
 * @param attendingCount 出席人数
 * @param seatCount 座位总数
 * @param assignedCount 已分配人数
 * @param unassignedCount 未分配人数
 * @param lockedCount 已锁定人数
 * @param availableSeatCount 可用座位数
 */
public record WorkspaceSummaryResult(
        String meetingId,
        String meetingName,
        int participantCount,
        int attendingCount,
        int seatCount,
        int assignedCount,
        int unassignedCount,
        int lockedCount,
        int availableSeatCount
) {
}
