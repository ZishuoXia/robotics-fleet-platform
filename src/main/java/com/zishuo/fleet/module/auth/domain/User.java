package com.zishuo.fleet.module.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户实体 - 系统的最终用户(企业内部员工)。
 *
 * <h2>核心字段说明</h2>
 * <ul>
 *   <li>tenant:用户所属的租户(多对一);用户被租户隔离</li>
 *   <li>username:用户名,在 tenant 内唯一(同一个 username 在不同租户可以共存)</li>
 *   <li>passwordHash:BCrypt 加盐哈希,从不存明文密码</li>
 *   <li>roles:用户被授予的角色集合(多对多)</li>
 * </ul>
 *
 * <h2>唯一性约束</h2>
 * {@code uk_users_tenant_username} 复合唯一约束:同一租户下 username 不能重复,
 * 但不同租户允许同名用户。这是多租户系统的标准做法。
 */
@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_users_tenant_username",
               columnNames = {"tenant_id", "username"}))
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户所属租户(多对一)。
     * {@code FetchType.LAZY}:默认懒加载,只在显式调用 user.getTenant() 时才查 DB。
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false) // 无论多对一还是一对多，都是给“多”的一方的表增加“一”的一方的主键作为一列外键。
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant; //一个 User 属于一个 Tenant",但"一个 Tenant 可以有很多 User"。这是典型的多对一关系(多个用户 → 一个租户)

    /** 用户名,租户内唯一 */
    @Column(nullable = false, length = 64)
    private String username;

    /**
     * 密码的 BCrypt 哈希。
     * 数据库列名是 password_hash,Java 字段名是 passwordHash(驼峰转下划线)。
     * 永远不存明文密码,即使开发环境也不行。
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 128)
    private String email;

    @Column(name = "full_name", length = 128)
    private String fullName;

    /** 是否启用;false 时此用户无法登录(但记录保留,便于审计) */
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * 用户被授予的角色集合(多对多)。
     * EAGER 加载是为了登录后立刻能拿到所有权限信息生成 JWT。
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    /**
     * 把所有角色拥有的权限"展平"成权限码集合。
     * 用于:
     * <ul>
     *   <li>生成 JWT 时塞进 perms claim</li>
     *   <li>构造 Spring Security 的 GrantedAuthority 集合</li>
     * </ul>
     */
    public Set<String> permissionCodes() {
        return roles.stream()                                       // 把 Set<Role> 变成流
                .flatMap(r -> r.getPermissions().stream())       // 每个 role 展开成它的 permissions 流
                .map(Permission::getCode)                        // 每个 permission 取 code 字符串
                .collect(Collectors.toSet());                     // 收集成 Set<String>
    }

    /** 用户的所有角色码,用于显示在用户资料里 */
    //简化版,只取角色码不去拿权限。用于在用户资料里显示"alice 是 ADMIN 和 AUDITOR"
    public Set<String> roleCodes() {
        return roles.stream().map(Role::getCode).collect(Collectors.toSet());
    }
}
