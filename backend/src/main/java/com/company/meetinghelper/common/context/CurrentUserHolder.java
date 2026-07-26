package com.company.meetinghelper.common.context;

import com.company.meetinghelper.common.security.CurrentUser;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 保存公司统一身份框架解析出的当前线程用户。
 */
public final class CurrentUserHolder {
    public static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();
    public static final AtomicInteger ACTIVE_BINDINGS = new AtomicInteger(0);

    private CurrentUserHolder() {
    }

    /**
     * 绑定当前线程用户。
     *
     * @param currentUser 当前用户
     */
    public static void set(CurrentUser currentUser) {
        if (CURRENT_USER.get() == null && currentUser != null) {
            ACTIVE_BINDINGS.incrementAndGet();
        }
        CURRENT_USER.set(currentUser);
    }

    /**
     * 获取当前线程用户。
     *
     * @return 当前用户，尚未接入统一身份时返回null
     */
    public static CurrentUser get() {
        return CURRENT_USER.get();
    }

    /**
     * 清理当前线程用户。
     */
    public static void clear() {
        if (CURRENT_USER.get() != null) {
            ACTIVE_BINDINGS.decrementAndGet();
        }
        CURRENT_USER.remove();
    }

    /**
     * 获取当前已绑定用户的线程数量。
     *
     * @return 活跃绑定数量
     */
    public static int activeBindings() {
        return ACTIVE_BINDINGS.get();
    }
}
