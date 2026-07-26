package com.company.meetinghelper.common.security;

import java.util.Set;

/**
 * 公司统一身份框架写入线程上下文的当前用户信息。
 *
 * @param userId 用户标识
 * @param displayName 用户显示名称
 * @param memberSpaceId 用户所属空间标识集合
 */
public record CurrentUser(String userId, String displayName, Set<String> memberSpaceId) {
}
