package com.zishuo.fleet.security.service;

import com.zishuo.fleet.module.auth.domain.User;
import com.zishuo.fleet.module.auth.repository.TenantRepository;
import com.zishuo.fleet.module.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户详情服务 - 把我们的 User 实体适配为 Spring Security 的 UserDetails 接口。
 *
 * <h2>适配模式</h2>
 * Spring Security 不知道我们的 User 类长什么样,它只认 UserDetails 接口。
 * 这个 Service 充当"翻译"角色,把 User 转成 UserDetails。
 *
 * <h2>多租户改造</h2>
 * 标准的 UserDetailsService 只接受 username,但我们是多租户系统,
 * 同一个 username 在不同租户可能并存。所以本类提供 loadByTenantAndUsername
 * 这种带租户的接口。
 */
//"认证方式"和"凭证形式"是两个独立的概念。JWT 是凭证形式,不是认证方式。M1 用"用户名密码认证 + 颁发 JWT 凭证",这是一种组合。
// FleetUserDetailsService 是"加载用户信息"的统一接口,M1 因为自己写了认证逻辑所以绕过了它,
// 但任何 Spring Security 内置的认证机制(OAuth、Basic、Remember-Me 等)都依赖它。预留它就是为未来扩展更多认证方式做准备——JWT 作为凭证保持不变,只是认证方式变多了。
@Service
@RequiredArgsConstructor
public class FleetUserDetailsService {
    //设计Service充当“翻译官” 好处:  User 类保持干净,只关心业务 框架依赖隔离在 Security 包里 想换框架?只改这个适配器,业务代码不动  这就是适配器模式的价值。

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    /**
     * 按租户码 + 用户名加载用户详情。
     * 通常 M1 我们直接通过 JWT 解析获取信息,这个方法主要为了未来扩展(如表单登录)预留。
     */
    @Transactional(readOnly = true) // 只读事务:数据库操作不修改数据,可以做额外优化
    public UserDetails loadByTenantAndUsername(String tenantCode, String username) {
        Long tenantId = tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Unknown tenant: " + tenantCode))
                .getId();

        User user = userRepository.findByTenantIdAndUsername(tenantId, username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User '" + username + "' not found in tenant '" + tenantCode + "'"));

        return toUserDetails(user);
    }

    /** Entity → UserDetails 的纯转换方法 */
    // 为什么要做 Set<String> → Set<GrantedAuthority> 的转换?
    // 因为最终消费方(AuthorizationFilter + @PreAuthorize)调用 getAuthorities() 得到的必须是 Collection<GrantedAuthority>。这是 Spring Security 的契约,你必须遵守。
    public UserDetails toUserDetails(User user) {
        Set<SimpleGrantedAuthority> authorities = user.permissionCodes().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet()); //把所有权限码转成 GrantedAuthority 集合

        // org.springframework.security.core.userdetails.User 是 Spring 提供的标准实现
        return org.springframework.security.core.userdetails.User //Spring Security 也有个叫 User 的类
                .withUsername(user.getUsername()) //.withUsername(...) 是 Spring 的 User 类的静态 builder 方法,启动一个链式构造流程. 你的 username → UserDetails.getUsername()
                .password(user.getPasswordHash())   // 你的 passwordHash → UserDetails.getPassword()  ← 改名!
                .authorities(authorities)            // 你的 permissionCodes 转 GrantedAuthority 集合
                .disabled(!user.isEnabled())         // 你的 enabled 取反 → UserDetails.isEnabled()
                .build();
    }
}
