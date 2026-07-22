package com.zishuo.fleet.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求 DTO。
 *
 * <p>新用户注册时,服务层会:
 * <ol>
 *   <li>校验租户是否存在</li>
 *   <li>检查用户名是否已被占用</li>
 *   <li>用 BCrypt 哈希密码</li>
 *   <li>赋予默认 VIEWER 角色</li>
 *   <li>保存到数据库</li>
 * </ol>
 */
public record RegisterRequest(
        @NotBlank
        String tenantCode,

        @NotBlank
        @Size(min = 3, max = 64)
        String username,

        /** 密码长度 8-128;实际生产应该再加复杂度要求(大小写、数字、符号) */
        @NotBlank
        @Size(min = 8, max = 128, message = "password must be 8-128 characters")
        String password,

        @Email //可以接受没传；但如果传了，必须是合法邮箱格式
        String email,

        @Size(max = 128)
        String fullName
) {}
