package com.zishuo.fleet.security.filter;

import com.zishuo.fleet.security.jwt.JwtService;
import com.zishuo.fleet.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器 - 在 Spring Security 过滤器链上拦截每个请求,提取并验证 JWT。
 *
 * <h2>执行流程</h2>
 * <ol>
 *   <li>读取 Authorization header</li>
 *   <li>没有或不是 Bearer 格式 → 直接放行(由后续过滤器处理"未认证"情况)</li>
 *   <li>有 token → 调 JwtService 解析</li>
 *   <li>解析成功 → 构造 Spring Security 的 Authentication 对象,放入 SecurityContext</li>
 *   <li>同时把 tenantId 放入 TenantContext,供业务代码使用</li>
 *   <li>处理完请求后,清理 TenantContext(防止线程复用导致泄漏)</li>
 * </ol>
 *
 * <h2>OncePerRequestFilter 是什么</h2>
 * Spring 提供的过滤器基类,保证同一个请求只执行一次过滤(防止内部转发导致重复执行)。
 */
@Slf4j
@Component
@RequiredArgsConstructor // Lombok 生成包含所有 final 字段的构造器(构造器注入)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        //第 1 步:读 Authorization Header
        String header = request.getHeader(HEADER);

        // 第 2 步:格式检查 + 早退
        // 没有 Authorization header 或格式不对 → 直接放行
        // 后续 Security 链会检查"是否需要认证",未认证访问受保护接口会触发 401
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        //第 3 步:提取 token 字符串
        String token = header.substring(PREFIX.length()).trim(); //substring(7)，分割点：从下标7字符开始一直到结尾;
        // 虽然少见，但某些代理或老旧客户端可能会在 Header 中引入换行符。.trim()能清除这些不可见字符，防止它们混入 Token，导致解析异常

        try {
            // 第 4 步:验证 token 并提取 claims
            Jws<Claims> jws = jwtService.parse(token);
            Claims claims = jws.getPayload();
            String username = claims.getSubject();

            // 第 5 步:提取 tenantId
            // tenantId 用 Number 接,因为 Jackson 反序列化数字默认是 Integer 而不是 Long,用 Number 接是兼容方案——Integer 和 Long 都是 Number 的子类
            // 直接 claims.get(..., Long.class) 会抛 ClassCastException
            Number tenantNum = claims.get(JwtService.CLAIM_TENANT_ID, Number.class);
            if (tenantNum == null) { // 兜底检查，正常情况 token 都有 tid(JwtService.issueAccessToken 时一定塞了)。但万一: 别的系统签发的 token 路过(没 tid)/老版本 token 还在用(以前没 tid)
                log.warn("JWT missing tenant claim");
                chain.doFilter(request, response);
                return;
            }
            Long tenantId = tenantNum.longValue();
            // 第 6 步:提取权限
            Set<String> permissions = JwtService.extractPermissions(claims);

            // 第 7 步:权限字符串 → GrantedAuthority，必须要转成GrantedAuthority类型，因为最终消费方(AuthorizationFilter + @PreAuthorize)
            // 调用 getAuthorities() 得到的必须是 Collection<GrantedAuthority>。这是 Spring Security 的契约,你必须遵守。
            // 把权限码转换为 Spring Security 的 GrantedAuthority
            // 这是 @PreAuthorize("hasAuthority(...)") 检查的依据
            Set<SimpleGrantedAuthority> authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());

            // 第 8 步:构造 Authentication 对象
            // 构造已认证的 Authentication,放入 SecurityContext
            // 第一个参数是 principal(身份),第二个是 credentials(密码,JWT 场景留空),第三个是权限
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken( //auth 是 Authentication 接口的一个实例。它代表"当前请求的认证状态"
                    username, null, authorities);
            // 第 9 步:设 Details(可选但推荐),Authentication.getDetails() 可以存"额外信息"——常见是请求的 IP 和 session ID。
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request)); //WebAuthenticationDetailsSource().buildDetails(request) 从 request 提取这些信息

            // 第 10 步:放进 SecurityContext
            SecurityContextHolder.getContext().setAuthentication(auth);
            // 第 11 步:放租户 ID
            TenantContext.set(tenantId);

            // "擦黑板",让下一个用这块黑板的人不看到上次的字迹
            // try/finally 确保即使后续抛异常,TenantContext 也会被清理,
            // 防止 Tomcat 线程复用时把租户 ID 泄漏给下一个请求
            try {
                chain.doFilter(request, response); //像"打开一扇门",门后面是所有后续 Filter + Controller + 业务代码 + 响应生成，期间TenantContext随时可以取到
            } finally {
                TenantContext.clear(); //无论如何，都要"擦黑板",让下一个用这块黑板的人不看到上次的字迹，这是我们自己实现的ThreadLocal,是复用的,要避免后续使用的人看到前面人的信息
            }
        } catch (JwtException ex) {
            // token 无效 → 不在这里写响应,让请求继续走,
            // 由后续的 AuthenticationEntryPoint 统一返回 401
            log.debug("Invalid JWT: {}", ex.getMessage());
            chain.doFilter(request, response);
        }
    }
}
