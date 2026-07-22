package com.zishuo.fleet.module.auth.repository;

import com.zishuo.fleet.module.auth.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 角色数据访问层。
 *
 * <p>注册新用户时会用 {@link #findByCode(String)} 找到 "VIEWER" 角色,
 * 自动赋给新用户作为默认权限。
 */
public interface RoleRepository extends JpaRepository<Role, Long> { //管理Role表，主键Long类型
    Optional<Role> findByCode(String code);
}
