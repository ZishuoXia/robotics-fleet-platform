package com.zishuo.fleet.tenant;

/**
 * 租户上下文 - 用 ThreadLocal 在请求处理过程中传递"当前租户 ID"。
 *
 * <h2>多租户(Multi-Tenancy)是什么</h2>
 * 一个系统服务多个客户(租户),每个租户的数据互相隔离。
 * 例如 CleanBot 公司是租户 A,SecurityBot 是租户 B,他们的设备列表互不可见。
 *
 * <h2>为什么用 ThreadLocal</h2>
 * Web 请求是线程隔离的(Tomcat 一个请求一个线程),用 ThreadLocal 存租户 ID,
 * 在请求处理的任何深度都能直接拿到,不用一层层把 tenantId 当参数传。
 *
 * <h2>必须 clear,否则线程池会泄漏</h2>
 * Tomcat 线程是复用的,处理完请求 A 的线程会被拿去处理请求 B。
 * 如果不在请求结束时 clear,请求 B 会拿到请求 A 的租户 ID,造成严重的数据泄漏。
 * 所以使用方必须 try / finally 配对调用 set 和 clear。
 *
 * <h2>典型使用</h2>
 * <pre>
 * try {
 *     TenantContext.set(tenantId);
 *     chain.doFilter(request, response);
 * } finally {
 *     TenantContext.clear();
 * }
 * </pre>
 *
 * @see com.zishuo.fleet.security.filter.JwtAuthenticationFilter 在这里设置和清理
 */
public final class TenantContext {

    /** ThreadLocal 容器:每个线程有自己独立的 Long 值 */
    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    /** 工具类禁止实例化 */
    private TenantContext() {}

    /** 设置当前线程的租户 ID(由 JWT 过滤器调用) */
    public static void set(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /** 获取当前线程的租户 ID;可能为 null(未认证请求) */
    public static Long get() {
        return CURRENT_TENANT.get();
    }

    /**
     * 强制要求当前线程必须有租户上下文,没有就抛异常。
     * 业务代码应该用这个,失败更显式。
     */
    public static Long requireCurrent() {
        Long id = CURRENT_TENANT.get();
        if (id == null) {
            throw new IllegalStateException("No tenant in context. "
                    + "This endpoint requires authentication.");
        }
        return id;
    }

    /** 清理 ThreadLocal,必须在请求结束时调用以防泄漏 */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
