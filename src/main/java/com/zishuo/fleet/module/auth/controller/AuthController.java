package com.zishuo.fleet.module.auth.controller;

import com.zishuo.fleet.common.response.ApiResponse;
import com.zishuo.fleet.module.auth.dto.LoginRequest;
import com.zishuo.fleet.module.auth.dto.RegisterRequest;
import com.zishuo.fleet.module.auth.dto.TokenResponse;
import com.zishuo.fleet.module.auth.dto.UserProfileResponse;
import com.zishuo.fleet.module.auth.service.AuthService;
import com.zishuo.fleet.security.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 认证模块 Controller - 提供登录、注册、查个人资料三个 REST 接口。
 *
 * <h2>三层架构</h2>
 * Controller 是最薄的一层 - 它的职责只是:
 * <ol>
 *   <li>接收 HTTP 请求,反序列化参数</li>
 *   <li>调用 Service 处理业务</li>
 *   <li>把结果包装成 ApiResponse 返回</li>
 * </ol>
 * <b>没有 try-catch</b>:异常自动向上抛,由 GlobalExceptionHandler 统一处理。
 *
 * <h2>API 版本号</h2>
 * 路径里的 {@code /api/v1/} 是版本号。以后接口要做不兼容修改,可以新增 v2,
 * 保留 v1 给老客户端,实现平滑过渡。
 */
@RestController                              // 标识为 REST controller,返回的对象会被 Jackson 序列化为 JSON
@RequestMapping("/api/v1/auth")              // 类级别的路径前缀
@RequiredArgsConstructor                     // 构造器注入
@Tag(name = "Auth", description = "Login, registration, and current-user lookup") // OpenAPI 分组
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    /**
     * 登录接口 - 公开访问。
     *
     * <p>{@code @SecurityRequirements} 空注解 = 覆盖 OpenApiConfig 全局的 bearerAuth 要求,
     * 这样 Swagger UI 不会要求登录接口本身先有 token。
     */
    @PostMapping("/login")
    @SecurityRequirements // 告诉 Swagger：“这个接口不需要任何安全认证方案。”@SecurityRequirements空注解 = 覆盖 OpenApiConfig 全局的 bearerAuth 要求,这样 Swagger UI 不会要求登录接口本身先有 token。
    @Operation(summary = "Exchange tenant/username/password for a JWT") //OpenAPI 文档描述
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        // @Valid 触发请求体的字段校验,失败抛 MethodArgumentNotValidException
        // @RequestBody 把 JSON 反序列化为 LoginRequest 对象
        return ApiResponse.ok(authService.login(req));
    }

    /**
     * 注册接口 - 公开访问。
     *
     * <p>{@code @ResponseStatus(CREATED)} 让成功响应返回 HTTP 201(更符合 REST 语义)。
     * 默认状态码：如果一个 @Controller方法正常返回（未抛异常），Spring MVC 默认返回 HTTP 200 (OK)
     * login和 me：它们符合默认行为，所以不需要额外注解。
     * register是一个 写操作，它改变了服务器状态（新增了用户）
     * register：它不符合默认行为，必须显式声明 201，否则会错误地返回 200 OK。
     */
    @PostMapping("/register")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user in an existing tenant")
    public ApiResponse<UserProfileResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(authService.register(req));
    }

    /**
     * 查询当前登录用户的资料 - 需要认证。
     *
     * <p>这个接口没有 @SecurityRequirements,所以继承全局的 bearerAuth 要求。
     */
    @GetMapping("/me")
    // 这里没有@SecurityRequirements
    @Operation(summary = "Get the profile of the currently authenticated user")
    public ApiResponse<UserProfileResponse> me() {
        Long userId = currentUserService.getCurrentUserId();
        return ApiResponse.ok(authService.currentUser(userId));
    }
}

class Solution {
    public String foreignDictionary(String[] words) {
        Map<Character, List<Character>> map = new HashMap<>();
        Map<Character, Integer> inDegree = new HashMap<>();
        for(String s : words){
            for(char c : s.toCharArray()){
                map.putIfAbsent(c, new ArrayList<>());
                inDegree.put(c, 0);
            }
        }
        for(int i = 0;i < words.length - 1;i++){
            String word1 = words[i], word2 = words[i + 1];
            if(word1.length() > word2.length() && word1.startsWith(word2))
                return "";
            int minLen = Math.min(word1.length(), word2.length());
            for(int j = 0;j < minLen;j++){
                if(word1.charAt(j) != word2.charAt(j)){
                    map.get(word1.charAt(j)).add(word2.charAt(j));
                    inDegree.put(word2.charAt(j), inDegree.get(word2.charAt(j))+1);
                    break;
                }
            }
        }
        Queue<Character> queue = new LinkedList<>();
        for(char c : inDegree.keySet()){
            if(inDegree.get(c) == 0)
                queue.offer(c);
        }
        StringBuilder sb = new StringBuilder();
        while(!queue.isEmpty()){
            char cur = queue.poll();
            sb.append(cur);
            for(char next : map.get(cur)){
                inDegree.put(next, inDegree.get(next) - 1);
                if(inDegree.get(next) == 0)
                    queue.offer(next);
            }
        }
        if(sb.length() != inDegree.size())
            return "";
        return sb.toString();
    }
}