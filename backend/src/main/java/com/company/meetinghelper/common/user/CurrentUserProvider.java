package com.company.meetinghelper.common.user;

import com.company.meetinghelper.common.context.CurrentUserHolder;
import com.company.meetinghelper.common.security.CurrentUser;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * CurrentUserProvider 类。
 */
@Component
public class CurrentUserProvider {

    /**
     * 从公司框架的线程上下文获取当前用户ID。
     *
     * @return 当前用户ID；演示阶段尚未绑定用户时返回空字符串
     */
    public String requireUserId() {
        CurrentUser user = CurrentUserHolder.get();
        return user == null ? "" : Objects.toString(user.userId(), "");
    }

    /**
     * 从公司框架的线程上下文获取当前用户显示名称。
     *
     * @return 当前用户显示名称；演示阶段尚未绑定用户时返回空字符串
     */
    public String currentUserName() {
        CurrentUser user = CurrentUserHolder.get();
        return user == null ? "" : Objects.toString(user.displayName(), "");
    }
}
