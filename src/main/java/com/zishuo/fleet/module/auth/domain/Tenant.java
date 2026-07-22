package com.zishuo.fleet.module.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 租户实体 - 多租户系统中的"客户/组织"概念。
 *
 * <h2>多租户的含义</h2>
 * 一个租户对应一个企业客户(例如"CleanBot 公司"、"SecurityRobot 公司")。
 * 该公司下的所有用户、设备等数据都通过 tenant_id 关联到这条 Tenant 记录,
 * 实现租户间的数据隔离。
 *
 * <h2>JPA 注解说明</h2>
 * <ul>
 *   <li>{@code @Entity}:声明这是一个 JPA 实体,会被映射到数据库表</li>
 *   <li>{@code @Table(name="tenants")}:对应的表名(默认是类名小写,显式写更清晰)</li>
 *   <li>{@code @Id} + {@code @GeneratedValue(IDENTITY)}:主键 + 自增策略
 *       (PostgreSQL 用的是 BIGSERIAL,所以用 IDENTITY)</li>
 * </ul>
 *
 * <h2>Lombok 注解</h2>
 * <ul>
 *   <li>{@code @Getter} / {@code @Setter}:生成所有 getter/setter</li>
 *   <li>{@code @NoArgsConstructor}:JPA 要求实体必须有无参构造器</li>
 *   <li>{@code @AllArgsConstructor} + {@code @Builder}:方便测试时构造对象</li>
 * </ul>
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor    // JPA 要求必须有无参构造器
@AllArgsConstructor   // 配合 @Builder 使用
@Builder              // 提供 Tenant.builder().code(...).name(...).build() 链式构造
public class Tenant extends AuditableEntity {

    /** 主键,数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 租户唯一标识符,登录时用(避免直接暴露内部 id);例:"default"、"cleanbot" */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /** 租户显示名称,用于界面;例:"CleanBot Inc." */
    @Column(nullable = false, length = 128)
    private String name;

    /** 租户是否启用;false 时该租户下的用户都无法登录 */
    @Column(nullable = false)
    private boolean enabled = true;
}
