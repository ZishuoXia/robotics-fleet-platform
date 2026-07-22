package com.zishuo.fleet.module.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * 角色实体 - 权限的命名集合,例如"管理员"、"操作员"、"只读用户"。
 *
 * <h2>为什么要有"角色"这一层</h2>
 * 直接给用户分配权限可以,但当用户多、权限细的时候,管理量爆炸。
 * 把一组常见的权限组合命名为"角色",分配给用户时只分配角色,简化管理。
 *
 * <h2>系统初始的三个角色(见 V3 seed 数据)</h2>
 * <ul>
 *   <li>ADMIN(管理员):拥有所有权限</li>
 *   <li>OPERATOR(操作员):能管设备和读用户</li>
 *   <li>VIEWER(只读):只能查看</li>
 * </ul>
 *
 * <h2>多对多关系</h2>
 * 一个角色可以有多个权限,一个权限可以属于多个角色。
 * JPA 通过 join table {@code role_permissions} 维护这个关系。
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色码,全局唯一,例 "ADMIN"、"OPERATOR" */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /** 角色显示名,例 "Administrator" */
    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 255)
    private String description;

    /**
     * 角色拥有的权限集合(多对多关系)。
     *
     * <p>{@code FetchType.EAGER}:加载 Role 时自动连带加载 permissions。
     * 这里用 EAGER 是因为 RBAC 检查时几乎一定要看权限,懒加载反而徒增麻烦。
     *
     * <p>{@code @JoinTable}:指定中间表的名字和外键列。
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();
}
