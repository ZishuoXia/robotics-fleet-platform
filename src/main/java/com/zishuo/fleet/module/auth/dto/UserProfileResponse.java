package com.zishuo.fleet.module.auth.dto;

import com.zishuo.fleet.module.auth.domain.User;

import java.util.Set;

/**
 * 用户资料响应 DTO - 用于 GET /api/v1/auth/me 等场景。
 *
 * <p><b>关键设计</b>:绝不直接返回 {@link User} 实体,
 * 否则 passwordHash 等敏感字段会暴露给前端。
 *
 * <p>静态方法 {@link #from(User)} 把 Entity 转换为 DTO,
 * 这是 Service → Controller 之间的标准模式。
 */
public record UserProfileResponse(
        Long id,
        String tenantCode,
        String username,
        String email,
        String fullName,
        Set<String> roles, //字符串集合,不是 Role/Permission 对象
        Set<String> permissions
        //passwordHash 绝对不能进 DTO(安全)
        //审计字段不暴露(内部信息)
        //enabled 不暴露(只给管理后台用)
) {
    /**
     * Entity → DTO 的转换方法。
     * 注意只取需要的字段,过滤掉 passwordHash 等敏感数据。
     */
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getTenant().getCode(),//Tenant 整个对象不要,只取 tenant.code(简化)
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.roleCodes(),//Role 整个对象不要,只取 role.code(简化)
                user.permissionCodes()//Permission 整个对象不要,只取 Permission.code(简化)
        );
    }
}
