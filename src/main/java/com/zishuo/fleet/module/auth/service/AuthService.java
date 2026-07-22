package com.zishuo.fleet.module.auth.service;

import com.zishuo.fleet.common.exception.BusinessException;
import com.zishuo.fleet.common.response.ErrorCode;
import com.zishuo.fleet.module.auth.domain.Role;
import com.zishuo.fleet.module.auth.domain.Tenant;
import com.zishuo.fleet.module.auth.domain.User;
import com.zishuo.fleet.module.auth.dto.LoginRequest;
import com.zishuo.fleet.module.auth.dto.RegisterRequest;
import com.zishuo.fleet.module.auth.dto.TokenResponse;
import com.zishuo.fleet.module.auth.dto.UserProfileResponse;
import com.zishuo.fleet.module.auth.repository.RoleRepository;
import com.zishuo.fleet.module.auth.repository.TenantRepository;
import com.zishuo.fleet.module.auth.repository.UserRepository;
import com.zishuo.fleet.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 认证服务 - 核心的登录、注册、查个人资料逻辑。
 *
 * <h2>三个核心方法</h2>
 * <ul>
 *   <li>{@link #login(LoginRequest)}:验证密码,签发 JWT</li>
 *   <li>{@link #register(RegisterRequest)}:创建新用户,赋默认角色</li>
 *   <li>{@link #currentUser(Long)}:根据 user id 查个人资料</li>
 * </ul>
 *
 * <h2>事务控制</h2>
 * 类上没标 @Transactional,而是每个方法单独标。这样可读性更好,
 * 而且查询方法可以用 @Transactional(readOnly=true) 优化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 新注册用户的默认角色,只读权限,管理员可以后续提升 */
    private static final String DEFAULT_ROLE = "VIEWER";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * 登录流程:租户码 + 用户名 + 密码 → JWT。
     *
     * <p><b>安全要点</b>:无论租户找不到、用户找不到、密码错,
     * 都返回同一个错误码 AUTH_INVALID_CREDENTIALS,防止用户名枚举攻击。
     */
    @Transactional // 被注解的方法，应该在一个数据库事务中执行。要么全部成功，要么全部失败回滚。
    public TokenResponse login(LoginRequest request) {
        // 1. 找租户 - 找不到也用 INVALID_CREDENTIALS,不暴露租户存在性
        Tenant tenant = tenantRepository.findByCode(request.tenantCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        // 2. 找用户 - 同样不区分"用户不存在"和"密码错"
        User user = userRepository
                .findByTenantIdAndUsername(tenant.getId(), request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        // 3. 检查账号是否启用
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED);
        }

        // 4. 验证密码 - BCrypt 用同样的盐重新哈希输入,对比是否一致
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        log.info("User {}/{} logged in", tenant.getCode(), user.getUsername());

        // 5. 签发 token
        String token = jwtService.issueAccessToken(user);
        return TokenResponse.bearer(token, jwtService.getExpirationSeconds());
    }

    /**
     * 注册流程:在指定租户下创建新用户,赋默认 VIEWER 角色。
     *
     * <p>M1 阶段注册接口是公开的,生产环境一般会改成"邀请制"或"管理员后台创建"。
     * register可以暴露，工程衡量，避免真人用户大量流失
     */
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        // 1. 验证租户存在
        Tenant tenant = tenantRepository.findByCode(request.tenantCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TENANT_NOT_FOUND));

        // 2. 检查用户名是否已被占用(租户内唯一)
        if (userRepository.existsByTenantIdAndUsername(tenant.getId(), request.username())) {
            throw new BusinessException(ErrorCode.AUTH_USERNAME_TAKEN);
        }

        // 3. 找默认角色  VIEWER是一个预置的、权限最低的系统角色，用于确保所有新用户从一个安全、可控的起点开始
        Role defaultRole = roleRepository.findByCode(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException( // 抛IllegalStateException:  这是 系统启动 / 种子数据问题  不是用户输入错误  应 快速失败（Fail Fast）
                        "Seed data missing — '" + DEFAULT_ROLE + "' role not found"));

        // 4. 构造并保存新用户
        User user = new User();
        user.setTenant(tenant);
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password())); // 哈希再存
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setEnabled(true);
        user.setRoles(Set.of(defaultRole));

        User saved = userRepository.save(user);
        log.info("Registered new user {}/{}", tenant.getCode(), saved.getUsername());
        return UserProfileResponse.from(saved);
    }

    /** 根据用户 ID 查询个人资料 */
    @Transactional(readOnly = true) //它提示数据库这是只读操作，可以优化执行计划、减少锁竞争
    public UserProfileResponse currentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        return UserProfileResponse.from(user); // 将内部实体 Device转换为对外暴露的 DeviceResponseDTO，避免将领域模型直接暴露给外部。
    }
}
