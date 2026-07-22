package com.zishuo.fleet.module.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 审计基类 - 提供 createdAt / updatedAt / createdBy / updatedBy 四个通用字段。
 *
 * <h2>设计模式:抽象基类</h2>
 * 业务实体(User、Tenant、Device 等)只要继承这个类,就自动拥有这四个字段。
 * 避免每个实体类都重复声明同样的字段。
 *
 * <h2>自动填充机制</h2>
 * <ul>
 *   <li>{@code @MappedSuperclass}:告诉 JPA 这是个基类,字段映射到子类对应的表</li>
 *   <li>{@code @EntityListeners(AuditingEntityListener.class)}:
 *       在 INSERT/UPDATE 前触发审计监听器,自动填充时间和用户字段</li>
 *   <li>{@code @CreatedDate} 等四个 Spring Data 注解,告诉监听器哪个字段填什么</li>
 * </ul>
 *
 * <h2>触发条件</h2>
 * 主类上的 {@code @EnableJpaAuditing} 必须开启,且要有 {@code AuditorAware} bean
 * (我们的 JpaAuditorAware)提供当前用户名。
 */
@Getter
@Setter
@MappedSuperclass // 表示这不是一个独立实体,但子类会继承它的字段映射
// 翻译成人话：数据库里 不会有一张叫 auditable_entity的表 但它的字段（created_at, updated_by等）会出现在 users、tenants、devices表里
@EntityListeners(AuditingEntityListener.class) // 让 JPA 在保存前调用审计监听器，Listener会调用AuditingHandler
public abstract class AuditableEntity { //abstract修饰，它是模板，不希望任何人直接 new AuditableEntity()，只能被继承

    /** 创建时间,只在 INSERT 时设置一次,UPDATE 时不变(updatable=false) */
    @CreatedDate
    //字段上的 @CreatedDate 就像是贴在物品上的标签——它本身不会动。需要一个"读标签的人"来读它、然后采取行动。
    //AuditingHandler 就是那个"读标签的人"。它通过反射"看到"字段上贴着 @CreatedDate 标签,才知道"哦,这个字段我得填创建时间"。
    //如果没有 AuditingHandler 用反射扫描:  字段上的 @CreatedDate 注解会一直存在,但没人理它 createdAt 字段永远是 null 数据库里这一列也是 null
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 最后修改时间,每次 UPDATE 都会刷新 */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 创建者,从 SecurityContext 自动取当前用户名 */
    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 64)
    private String createdBy;

    /** 最后修改者 */
    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}
