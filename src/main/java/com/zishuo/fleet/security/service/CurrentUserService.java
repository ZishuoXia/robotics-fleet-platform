package com.zishuo.fleet.security.service;

import com.zishuo.fleet.common.exception.BusinessException;
import com.zishuo.fleet.common.response.ErrorCode;
import com.zishuo.fleet.module.auth.domain.User;
import com.zishuo.fleet.module.auth.repository.UserRepository;
import com.zishuo.fleet.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 当前用户服务 - 业务代码用这个统一接口获取"现在是谁在调用"。
 *
 * <h2>解决的问题</h2>
 * 没有这个 Service 的话,每个 Service 都要自己写:
 * <pre>
 * Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 * String username = auth.getName();
 * Long tenantId = TenantContext.requireCurrent();
 * User user = userRepository.findByTenantIdAndUsername(tenantId, username).orElseThrow(...);
 * </pre>
 * 几十个 Service 重复几十遍。封装到这里,大家直接调 {@code currentUserService.getCurrentUser()}。
 *
 * <h2>数据来源</h2>
 * <ul>
 *   <li>用户名:从 Spring Security 的 SecurityContextHolder 取</li>
 *   <li>租户 ID:从我们自己的 TenantContext 取</li>
 *   <li>完整 User 实体:用前两者拼起来去 DB 查</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    /** 获取完整的当前用户实体(去 DB 查询) */
    @Transactional(readOnly = true) // 只读事务:数据库操作不修改数据,可以做额外优化
    public User getCurrentUser() { //查完整用户实体
        Authentication auth = SecurityContextHolder.getContext().getAuthentication(); //获取JwtAuthenticationFilter先前用同一个ThreadLocal存储到SecurityContext中的auth
        if (auth == null || !auth.isAuthenticated()) { //正常情况这两个判断不会触发——因为业务代码运行时(Controller 里),JWT Filter 已经把认证信息设好了。
            // 但加防御性检查是好习惯: 万一这个 Service 被定时任务或别的非 Web 入口调用; 万一以后改架构导致 Filter 没执行; 万一测试代码绕过了认证...
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long tenantId = TenantContext.requireCurrent();
        String username = auth.getName();
        return userRepository.findByTenantIdAndUsername(tenantId, username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
                        "Authenticated user not found in database"));
    }

    /** 只取用户 ID,不查整个实体(性能更好) */
    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    /** 只取租户 ID,直接读 ThreadLocal 不查 DB */
    public Long getCurrentTenantId() {
        return TenantContext.requireCurrent();
    }
}
