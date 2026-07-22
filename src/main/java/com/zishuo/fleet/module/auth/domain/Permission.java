package com.zishuo.fleet.module.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 权限实体 - RBAC(Role-Based Access Control)的最细粒度单元。
 *
 * <h2>RBAC 三元组</h2>
 * 用户(User) ←多对多→ 角色(Role) ←多对多→ 权限(Permission)
 *
 * <h2>权限码的命名规范</h2>
 * 形如 "资源:动作",例如:
 * <ul>
 *   <li>{@code device:read}  - 查看设备</li>
 *   <li>{@code device:write} - 创建/修改设备</li>
 *   <li>{@code user:read}    - 查看用户</li>
 * </ul>
 * 这种格式便于用通配符匹配(如 "device:*" 表示设备的所有操作)。
 *
 * <h2>权限在 JWT 中的体现</h2>
 * 用户登录后,所有授予的权限码会作为 JWT claim 写入 token。
 * Controller 上 {@code @PreAuthorize("hasAuthority('device:write')")} 检查这些权限码。
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 权限码,全局唯一,例 "device:read" */
    @Column(nullable = false, unique = true, length = 128)
    private String code;

    /** 权限的人类可读描述,用于管理后台展示 */
    @Column(length = 255)
    private String description;
}
