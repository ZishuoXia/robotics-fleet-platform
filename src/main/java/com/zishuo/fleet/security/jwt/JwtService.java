package com.zishuo.fleet.security.jwt;

import com.zishuo.fleet.module.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * JWT 工具服务 - 生成和验证 token。
 *
 * <h2>JWT 是什么</h2>
 * JSON Web Token,一种"自包含"的 token 格式。结构是三段用 . 分隔的 base64:
 * <pre>
 * Header.Payload.Signature
 * </pre>
 * Payload 里装着用户信息(claims),Signature 是用密钥对前两段的签名,防篡改。
 *
 * <h2>JWT 的优势(相对 Session)</h2>
 * <ul>
 *   <li>无状态:服务器不需要存 session,横向扩展简单</li>
 *   <li>跨域友好:前端把 token 放 header 即可,不依赖 cookie</li>
 *   <li>携带信息:用户 id、权限等可以直接放 token 里,免去查 DB</li>
 * </ul>
 *
 * <h2>本类用 jjwt 0.12.x 的 API</h2>
 * 注意网上很多教程是 0.11 的旧 API({@code setIssuer}、{@code setSubject}),已废弃。
 * 0.12 用的是 {@code issuer()}、{@code subject()} 等流式 API。
 */
@Service
@EnableConfigurationProperties(JwtProperties.class) // 让 Spring 把 JwtProperties 注册为 bean
public class JwtService {

    // claim key 常量,统一管理
    public static final String CLAIM_TENANT_ID = "tid";
    public static final String CLAIM_USERNAME = "sub"; // sub 是 JWT 标准里的"subject"
    // sub 是 JWT 标准定义的字段,有专门的 getter (claims.getSubject())。我们把常量定义出来主要是为了文档化,实际签发时用 jjwt 的 subject() 方法
    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_PERMISSIONS = "perms";

    private final JwtProperties properties;

    /**
     * HMAC 签名密钥,从配置的 base64 secret 解码而来。
     * SecretKey 是 javax.crypto 的标准接口,jjwt 接受这种类型。
     */
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // base64 解码 → 原始字节 → 构造 HMAC 密钥
        // 如果 secret 解码后不足 256 位,Keys.hmacShaKeyFor 会直接抛异常
        byte[] keyBytes = Decoders.BASE64.decode(properties.getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 为已认证的用户签发 access token。
     *
     * <p>放进 token 的信息:
     * <ul>
     *   <li>iss(issuer):防止其他系统的 token 在我们这里被错误接受</li>
     *   <li>sub(subject):用户名</li>
     *   <li>iat / exp:签发和过期时间</li>
     *   <li>uid:用户 id(避免每次请求都要根据用户名查 DB)</li>
     *   <li>tid:租户 id(用于多租户隔离)</li>
     *   <li>perms:权限码集合(免去查 DB 即可做权限检查)</li>
     *   Claim 是 JWT 的Payload中，用来存放自定义数据的键值对
     *   iss, sub, iat, exp是 JWT 标准规定的 Claim。
     *   uid, tid, perms是 项目自定义的 Claim
     * </ul>
     */
    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getExpirationSeconds());
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getUsername()) // 不是 .claim("sub", ...)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_TENANT_ID, user.getTenant().getId())
                .claim(CLAIM_PERMISSIONS, user.permissionCodes())
                .signWith(signingKey, Jwts.SIG.HS256) // HMAC-SHA256 算法签名
                .compact(); // 输出最终的 token 字符串
    }

    /**
     * 解析并验证 token,返回 claims。
     * 任何失败(签名错、过期、issuer 不匹配)都会抛 JwtException。
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey) // 验证签名用 signingKey。 这是关键!没这一步,parser 不会验证签名,任何人伪造的 token 都能被解析
                .requireIssuer(properties.getIssuer()) // 强制 issuer 校验
                .build()
                .parseSignedClaims(token);
    }

    public long getExpirationSeconds() {
        return properties.getExpirationSeconds();
    }

    /**
     * 从 claims 中安全地提取权限码集合。
     * 因为 JSON 反序列化时类型可能是 List 或 ArrayList,这里统一转换。
     * 签发时,我们传的是 Set<String>
     * 但当 JWT 被 base64 解码、JSON 反序列化回来时,类型变了——可能是 List<String>、ArrayList<String>、甚至 Object 数组。具体取决于底层 JSON 库
     */
    @SuppressWarnings("unchecked")
    public static Set<String> extractPermissions(Claims claims) { //这个方法的作用是 从 JWT 的 Payload（Claims）中，
        // 把 perms这个自定义 Claim 安全地取出来，并转换成一个不可修改的 Set<String>，供 Spring Security 做权限校验使用
        Object raw = claims.get(CLAIM_PERMISSIONS);
        if (raw instanceof java.util.Collection<?> coll) { //确认从 JWT 里取出来的东西，确实是一个集合
            return coll.stream() //开启流，集合是“容器”，流是“流水线”。只有开启流，才能进行后续的 map转换操作
                    .map(Object::toString) //通过强制调用 toString()，无论原始元素是什么类型（只要是对象），最终都能安全地变成一个字符串，确保类型安全。
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()); //不可修改（Unmodifiable）的 Set,它防止了权限集合在传递或后续处理过程中被意外或恶意添加/删除，保证了权限数据的不可变性
        }
        return Set.of();
    }
}

