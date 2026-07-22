package com.zishuo.fleet.common.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA 审计 - 告诉 Spring Data "当前操作的用户是谁",
 * 用于自动填充 @CreatedBy 和 @LastModifiedBy 字段。
 *
 * <h2>工作流程</h2>
 * <ol>
 *   <li>用户带 JWT 调接口</li>
 *   <li>JWT 过滤器把用户名放入 SecurityContextHolder</li>
 *   <li>Service 调 repository.save() 保存实体</li>
 *   <li>Hibernate 触发审计事件,调用此类的 getCurrentAuditor()</li>
 *   <li>本类返回当前用户名,Hibernate 自动设置到 createdBy/updatedBy 字段</li>
 * </ol>
 *
 * <h2>降级处理</h2>
 * 在数据库迁移、定时任务等没有用户上下文的场景,返回 "system" 作为审计者。
 *
 * @see com.zishuo.fleet.module.auth.domain.AuditableEntity 使用审计字段的实体基类
 */

//什么时候你能"看到"它工作
//完成 M1、登录后调任何写接口(比如创建设备)
//它的"使用方"不是你写的业务代码,而是 Spring Data 的审计监听器——监听器在背后调它
@Component("auditorAware")    // ← bean 叫 "auditorAware",和主类的配置对上
public class JpaAuditorAware implements AuditorAware<String> {
//                                       ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
//        Spring Data 定义的接口,要求实现 getCurrentAuditor() 方法
//        泛型 <String> 表示 createdBy/updatedBy 字段的类型是 String

    @Override
    public Optional<String> getCurrentAuditor() {
        // 从 Spring Security 上下文取当前认证信息
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // 没有用户上下文的情况(系统启动、定时任务等),用 "system" 兜底
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.of("system");
        }

        // 返回当前登录用户的用户名
        return Optional.of(auth.getName());
    }
}
