package com.zishuo.fleet.security.jwt;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 配置 - 把 application.yml 里 {@code app.jwt.*} 的值绑定到这个类的字段。
 *
 * <h2>外部化配置的好处</h2>
 * <ul>
 *   <li>密钥、过期时间不写死在代码里</li>
 *   <li>不同环境(dev/prod)用不同配置文件</li>
 *   <li>生产环境可以用环境变量覆盖(APP_JWT_SECRET)</li>
 * </ul>
 *
 * <h2>启动时校验</h2>
 * {@code @Validated} + 字段上的 {@code @NotBlank/@Min} 让 Spring 在启动时检查配置。
 * 配置错(比如忘记设密钥)立即报错,而不是等到第一次请求才发现。
 */
@Getter
@Setter
@Validated //如果你在一个类或方法上只写 @Validated，而没有在其字段或参数上写 @NotBlank、@NotNull等约束注解，那么它不会有任何效果
@ConfigurationProperties(prefix = "app.jwt") // 绑定到 application.yml 的 app.jwt.* 节点
public class JwtProperties {

    /**
     * Base64 编码的 HMAC-SHA256 密钥。
     * 解码后必须至少 32 字节(256 位),这是 RFC 7518 §3.2 的要求。
     */
    @NotBlank
    private String secret;

    /** access token 的有效期(秒);默认 1 小时 */
    @Min(60) //最短不能小于60秒
    private long expirationSeconds = 3600;

    /** JWT 的 issuer claim,用于防止跨系统的 token 滥用 */ //场景:大公司的多个系统共用一套用户，例如字节旗下有抖音，飞书......
    @NotBlank
    private String issuer = "robotics-fleet-platform";
}
