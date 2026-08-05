package com.company.meetinghelper.agent.tool.query;

import java.util.List;

/**
 * 智能体查询分页结果。
 *
 * @param <T> 条目类型
 * @param total 匹配总数
 * @param returned 实际返回数
 * @param summary 面向智能体的结果摘要
 * @param items 返回条目
 */
public record AgentPageResult<T>(
        int total,
        int returned,
        String summary,
        List<T> items
) {
}
