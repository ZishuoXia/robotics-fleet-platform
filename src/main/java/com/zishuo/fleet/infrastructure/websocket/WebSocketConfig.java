package com.zishuo.fleet.infrastructure.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP 协议配置。
 *
 * <h2>STOMP 是什么?</h2>
 * STOMP (Simple Text Oriented Messaging Protocol) 是一个建立在 WebSocket 之上的
 * 消息协议。原始 WebSocket 只是"双向字节流",STOMP 在上面定义了"主题订阅模型":
 * <ul>
 *   <li>客户端 SUBSCRIBE /topic/foo,服务端 SEND /topic/foo 时所有订阅者都收到</li>
 *   <li>有点像 Kafka 的 pub/sub,但在浏览器和服务端之间</li>
 *   <li>SockJS.js + Stomp.js 是前端常用的客户端库</li>
 * </ul>
 *
 * <h2>本配置干了什么</h2>
 * <ol>
 *   <li>开放 WebSocket 端点 /ws/devices(SockJS 后备支持)</li>
 *   <li>启用基于内存的 SimpleBroker,前缀为 /topic 的目标交给它转发</li>
 *   <li>设置应用前缀 /app,客户端 SEND /app/xxx 时路由到 @MessageMapping 方法</li>
 *   <li>把 WebSocketAuthInterceptor 注册到 inbound channel,做 JWT 认证</li>
 * </ol>
 *
 * <h2>多租户隔离的实现</h2>
 * 后端推送时 SEND /topic/tenant/{tenantId}/devices,
 * 客户端 SUBSCRIBE /topic/tenant/{自己的tenantId}/devices,
 * 这样只有同租户的客户端能收到。
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    /**
     * 注册 STOMP 端点 - 客户端从这里发起 WebSocket 握手。
     * setAllowedOriginPatterns("*") 是开发期方便,生产应该改为白名单域名。
     * withSockJS() 开启 SockJS 后备,旧浏览器不支持 WebSocket 时降级用长轮询。
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/devices")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * 配置消息代理(message broker)。
     * <ul>
     *   <li>enableSimpleBroker("/topic"):用 Spring 内置的内存代理,
     *       前缀 /topic 的目标(目的地)由它处理。
     *       生产环境量大可换成 RabbitMQ / ActiveMQ 等外部代理。</li>
     *   <li>setApplicationDestinationPrefixes("/app"):客户端 SEND /app/xxx,
     *       Spring 会路由到 @MessageMapping("/xxx") 标记的方法。
     *       我们本期主要做"服务端推送",客户端不发消息,所以这个用不到。</li>
     * </ul>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * 把认证拦截器注册到 inbound channel。
     * 所有从客户端到服务端的 STOMP 帧都会经过它,我们在这里拦截 CONNECT 做 JWT 校验。
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
