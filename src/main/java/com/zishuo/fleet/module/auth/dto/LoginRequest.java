package com.zishuo.fleet.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求 DTO。
 *
 * <h2>用 Java record 而不是 class</h2>
 * record 是 Java 14+ 引入的"不可变数据载体":
 * <ul>
 *   <li>所有字段自动 final + private</li>
 *   <li>自动生成构造器、getter(用字段名作方法名)、equals、hashCode、toString</li>
 *   <li>极简语法,适合纯数据传输对象</li>
 * </ul>
 *
 * <h2>为什么 DTO 和 Entity 要分开</h2>
 * <ul>
 *   <li>Entity 是数据库映射,可能包含敏感字段(passwordHash)</li>
 *   <li>DTO 是 API 契约,严格定义前端能传什么、能收什么</li>
 *   <li>Entity 字段改名会影响数据库 schema,DTO 不会</li>
 * </ul>
 *
 * <h2>校验注解</h2>
 * {@code @NotBlank} 在 Controller 用 {@code @Valid} 时生效,
 * 不通过时由 GlobalExceptionHandler 的 handleValidation() 处理。
 */
public record LoginRequest(
        @NotBlank(message = "tenantCode is required")
        String tenantCode,
        @NotBlank(message = "username is required")
        String username,
        @NotBlank(message = "password is required")
        String password
) {}
