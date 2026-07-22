package com.zishuo.fleet.module.auth.repository;

import com.zishuo.fleet.module.auth.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 租户数据访问层。
 *
 * <h2>Spring Data JPA 的"魔法"</h2>
 * 我们只声明接口,不写实现 - Spring Data 启动时:
 * <ol>
 *   <li>扫描到这个 interface</li>
 *   <li>自动生成实现类</li>
 *   <li>把实现类注册为 bean</li>
 * </ol>
 * 服务层 @Autowired TenantRepository 时,拿到的就是这个自动生成的实现。
 *
 * <h2>方法名解析</h2>
 * Spring Data 解析方法名生成 SQL,例如:
 * <ul>
 *   <li>{@code findByCode(String code)} → {@code SELECT * FROM tenants WHERE code = ?}</li>
 *   <li>{@code existsByCode(String code)} → {@code SELECT COUNT(*) > 0 FROM tenants WHERE code = ?}</li>
 * </ul>
 *
 * <h2>JpaRepository 自带的方法</h2>
 * 继承自 JpaRepository 的方法包括:save、findAll、findById、count、deleteById 等。
 * 不需要自己写。
 */
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    //第一个参数 Tenant：指定这个 Repository 管理的是 Tenant实体
    //第二个参数 Long：指定 Tenant 实体的 主键类型
    //
    /**
     * 通过租户码查找,返回 Optional 强制调用方处理"找不到"的情况。
     * 比返回 null 更安全(Java 8+ 推荐做法)。
     * Spring 通过这两个泛型参数知道:
     *
     * "这个仓库管的是 Tenant 表"
     * "Tenant 的主键是 Long 类型"
     */
    Optional<Tenant> findByCode(String code);
    //Optional<Tenant> 表示:可能查到(返回 Optional.of(tenant)),可能查不到(返回 Optional.empty())。
}
