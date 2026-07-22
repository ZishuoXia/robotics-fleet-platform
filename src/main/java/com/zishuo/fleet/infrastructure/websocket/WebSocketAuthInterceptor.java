package com.zishuo.fleet.infrastructure.websocket;

import com.zishuo.fleet.security.jwt.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebSocket(STOMP)认证拦截器 - 在客户端发 CONNECT 帧时验证 JWT。
 *
 * <h2>为什么 WebSocket 认证不像 HTTP 那么直接?</h2>
 * HTTP 的认证靠 JwtAuthenticationFilter,每个请求都校验 Header。
 * WebSocket 不一样:
 * <ul>
 *   <li>建立连接时是一次 HTTP 握手,之后变成长连接</li>
 *   <li>长连接期间客户端不再带 Header,服务端必须在 CONNECT 时一次性认证</li>
 *   <li>认证信息(tenantId、userId)要保存在 STOMP Session 里</li>
 * </ul>
 *
 * <h2>本拦截器做了什么</h2>
 * <ol>
 *   <li>拦截 STOMP CONNECT 帧</li>
 *   <li>从 Authorization header 取 JWT</li>
 *   <li>用 JwtService 验证</li>
 *   <li>验证通过:把 tenantId / userId 存到 Session attributes</li>
 *   <li>验证失败:抛异常,WebSocket 连接被拒绝</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    public static final String SESSION_TENANT_ID = "tenantId";
    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_USERNAME = "username";

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        // 只在 CONNECT 帧时做认证
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        if (token == null) {
            log.warn("WebSocket CONNECT rejected: no Authorization header");
            throw new IllegalArgumentException("Missing JWT in CONNECT frame");
        }

        try {
            Jws<Claims> jws = jwtService.parse(token);
            Claims claims = jws.getPayload();

            // 用 Number 兼容 Integer/Long(JSON 反序列化时可能是 Integer)
            Number tenantNum = claims.get(JwtService.CLAIM_TENANT_ID, Number.class);
            Number userNum = claims.get(JwtService.CLAIM_USER_ID, Number.class);
            String username = claims.getSubject();

            if (tenantNum == null) {
                throw new IllegalArgumentException("JWT missing tenant claim");
            }

            // 存到 Session,后续帧可以读取
            accessor.getSessionAttributes().put(SESSION_TENANT_ID, tenantNum.longValue());
            if (userNum != null) {
                accessor.getSessionAttributes().put(SESSION_USER_ID, userNum.longValue());
            }
            accessor.getSessionAttributes().put(SESSION_USERNAME, username);

            log.info("WebSocket connected: user={} tenant={}", username, tenantNum.longValue());
        } catch (Exception e) {
            log.warn("WebSocket CONNECT rejected: invalid JWT - {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JWT in CONNECT frame", e);
        }
    }

    private String extractToken(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            return null;
        }
        String header = authHeaders.get(0);
        if (header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }
}
