package com.zishuo.fleet.security.config;

import com.zishuo.fleet.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 主配置 - 定义整个项目的安全策略。
 * <h2>角色</h2>
 * <ol>整个 Spring Security 的"总配置"——告诉 Spring Security:
 *   <li>哪些路径需要登录,哪些不需要
 *   <li>JWT Filter 插在哪
 *   <li>如何处理 401 和 403
 *   <li>用什么密码哈希算法
 *   <li>CSRF / CORS / Session 怎么配置
 *   <li>启用方法级权限注解(@PreAuthorize)
 * </ol>它不写业务逻辑,只写"配置"——告诉框架怎么运作。一句话：这是个"装配车间",把前面所有零件组装成一台能工作的"安全机器"。
 * <h2>配置要点</h2>
 * <ol>
 *   <li>无状态(STATELESS):不用服务端 session,认证状态完全由 JWT 携带</li>
 *   <li>禁用 CSRF:CSRF 是针对 cookie 认证的攻击,JWT 用 header 不需要</li>
 *   <li>白名单公开端点:登录、注册、健康检查、Swagger</li>
 *   <li>所有其他端点必须认证</li>
 *   <li>JwtAuthenticationFilter 插入到标准用户名密码过滤器之前</li>
 *   <li>认证失败 / 权限不足时,用我们的 JSON 处理器(而非 HTML 错误页)</li>
 * </ol>
 *
 * <h2>为什么用 BCrypt 而不是 MD5/SHA256</h2>
 * <ul>
 *   <li>BCrypt 自带盐(salt),抵抗彩虹表攻击</li>
 *   <li>BCrypt 是慢哈希,故意设计成每次哈希要 100ms 左右,大大增加暴力破解成本</li>
 *   <li>cost factor(强度因子)可调,硬件升级时可以提高</li>
 * </ul>
 */
@Configuration // 标记为配置类
@EnableMethodSecurity // 激活 @PreAuthorize 等方法级权限注解，并且是AOP机制，后续发现有 @PreAuthorize 注解的方法，都要在方法调用前插入权限检查
//有了这一行,以下注解才会生效: @PreAuthorize("hasAuthority('device:write')") — 调用前检查 @PostAuthorize — 调用后检查 @PreFilter / @PostFilter — 集合过滤
@RequiredArgsConstructor
public class SecurityConfig {

    /** 公开端点白名单,无需认证即可访问 */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",       // 登录
            "/api/v1/auth/register",    // 注册
            "/api/v1/auth/me",           //当前用户
            "/api/v1/health",           // 健康检查
            "/actuator/health",         // Actuator 健康检查
            "/actuator/info",
            "/v3/api-docs/**",          // OpenAPI JSON 规范，**代表当前目录下的任意层级，也即所有子目录
            "/v3/api-docs.html",
            "/swagger-ui/**",           // Swagger UI 静态资源
            "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    /**
     * 密码编码器 - 全局唯一的 PasswordEncoder bean,
     * 任何需要哈希/校验密码的地方都注入它(AuthService、UserDetailsService)。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 默认强度 10,即 2^10 轮哈希;负责密码的“哈希（Hash）”与“校验”，是保障用户密码安全的唯一工具
    }

    /**
     * 暴露 AuthenticationManager 为 bean,Spring Security 5+ 不再自动暴露,需要手动声明。
     * 我们在 AuthService 里需要它来做用户名密码验证(虽然 M1 用了更直接的方式)。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception { //从 Spring Security 的配置中，获取并暴露“认证总指挥” AuthenticationManager
        // AuthenticationManager是一个接口，它是 Spring Security 认证流程的总入口，它不关心你是 JWT、表单登录还是指纹登录，它只负责指挥
        // AuthenticationConfiguration它是一个配置类，这是 Spring Security 提供的 “配置助手”。 它内部已经配置好了：
        // 认证所需的全部基础设施（如 UserDetailsService、PasswordEncoder）。 它知道如何组装出一个可用的 AuthenticationManager
        return cfg.getAuthenticationManager(); // 这是一个 工厂方法。 它会自动发现你配置好的 UserDetailsService和 PasswordEncoder。 组装并返回一个 AuthenticationManager实例
    }

    /**
     * 安全过滤器链 - Spring Security 6 的现代配置方式(替代过时的 WebSecurityConfigurerAdapter)。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF:JWT 不依赖 cookie,不存在跨站请求伪造风险；如果保留 CSRF 启用,每个 POST/PUT/DELETE 请求都要先 GET 拿 token 再带上,对前后端分离的 API 是巨大负担
                .csrf(AbstractHttpConfigurer::disable)
                // 禁用 CORS:M1 阶段没有跨域需求,后期需要再开
                .cors(AbstractHttpConfigurer::disable) //我们目前没配 CorsConfigurationSource 所以这一行实际上没真正启用 CORS——但它告诉 Spring Security "CORS 过滤器请就位",等以后补 CorsConfigurationSource Bean 时自动生效
                // 无状态会话:不创建 HttpSession,认证完全靠 JWT
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) //第一个是参数，是当前方法所需
                // 的参数的数据类型，而这个类型是一个函数式接口，于是再去看函数式接口中的唯一抽象方法的参数数据类型，然后这里的第一个参数也就是唯一抽象方法的“参数数据类型”，接下来，就是调用这个数据类型中的方法
                // 路径授权规则
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll() // 白名单
                        .anyRequest().authenticated())                  // 其他都要认证 (公开路径放行,其他都要登录。)
                // 异常处理器 - 让 401/403 返回我们的 JSON 格式
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint) // 注入的 Bean 被传进去,Spring Security 内部记下来,需要时调用，这里用的就是我们自己实现的Json开头的了
                        .accessDeniedHandler(accessDeniedHandler))
                // 把 JWT 过滤器插到标准用户名密码过滤器之前
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);
        return http.build(); //链式调用结束,生成最终的 SecurityFilterChain 对象,返回。Spring 看到这个 Bean,用它作为过滤器链配置
    }
}
