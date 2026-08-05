package com.company.meetinghelper.agent.tool.query;

/**
 * 智能体可读的座位摘要。
 *
 * @param id 座位ID
 * @param name 座位名称
 * @param occupiedParticipantName 占用人员姓名
 * @param occupiedEmployeeNo 占用人员工号
 */
public record SeatBrief(
        String id,
        String name,
        String occupiedParticipantName,
        String occupiedEmployeeNo
) {
}
