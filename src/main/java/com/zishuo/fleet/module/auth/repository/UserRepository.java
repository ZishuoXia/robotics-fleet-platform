package com.zishuo.fleet.module.auth.repository;

import com.zishuo.fleet.module.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问层。
 *
 * <p>所有查询都带 tenantId 参数,体现多租户的核心原则:
 * <b>查询必须以租户为边界</b>,绝不允许跨租户查用户。
 * jpa魔法：自定义查询用方法名解析，你只声明方法签名,Spring 用反射 + 动态代理在运行时生成实现
 */
public interface UserRepository extends JpaRepository<User, Long> { //查User表，主键类型Long

    /** 在指定租户内按用户名查询(登录、查个人资料等) */
    Optional<User> findByTenantIdAndUsername(Long tenantId, String username);
    //也就代表这个jpql语句:SELECT u FROM User u WHERE u.tenant.id = ?1 AND u.username = ?2

    /** 检查用户名在租户内是否已存在(注册时的去重检查) */
    boolean existsByTenantIdAndUsername(Long tenantId, String username);
}
