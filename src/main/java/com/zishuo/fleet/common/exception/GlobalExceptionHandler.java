package com.zishuo.fleet.common.exception;

import com.zishuo.fleet.common.response.ApiResponse;
import com.zishuo.fleet.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Catches exceptions thrown anywhere in the request pipeline and converts
 * them into uniform {@link ApiResponse} error envelopes.
 *
 * <p>Specific handlers come first; the catch-all sits last and deliberately
 * hides the original message — internal stack traces should never reach a
 * client.
 */
@Slf4j
@RestControllerAdvice
//加了这个注解,Spring 启动时:
//扫描到这个类
//注册它为"异常监听器"
//任何 Controller 抛异常,Spring 先来这个类找匹配的处理方法
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    //如果某个 Controller 抛了 BusinessException 类型的异常,就调用这个方法来处理,把异常对象作为参数传进来(参数 ex)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("Business error: code={} message={}",
                ex.getErrorCode().getCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    //参数校验失败
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", detail);
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, detail));
    }

    /**
     * Static resource not found (e.g. /favicon.ico, or the user hits a path
     * with no matching controller). This is a client-side mistake — return
     * 404 instead of letting the catch-all turn it into a 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    //用户访问的 URL 没有对应的 Controller(比如 GET /api/v1/nonexistent)  404处理
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        log.debug("No resource found: {}", ex.getResourcePath());
        return ResponseEntity
                .status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.NOT_FOUND,
                        "Path not found: " + ex.getResourcePath()));
    }

    //Handler D-F:Spring Security 异常
    //实际上 Spring Security 的过滤器链在 Controller 之前,这些异常未必会传到 GlobalExceptionHandler。等到 Security 阶段再讲
    @ExceptionHandler(BadCredentialsException.class)
    // 用户名密码错
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials");
        return ResponseEntity
                .status(ErrorCode.AUTH_INVALID_CREDENTIALS.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.AUTH_INVALID_CREDENTIALS));
    }

    @ExceptionHandler(AuthenticationException.class)
    // 其他认证失败(token 无效等)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
                .status(ErrorCode.UNAUTHORIZED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    // 已登录但权限不够
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    //兜底，其他所有 handler 都没匹配上时——也就是发生了意外的、可能是 bug 的异常(NullPointerException、数据库连接断了等)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }
    class Solution {
        public List<List<String>> partition(String s) {
            List<List<String>> res = new ArrayList<>();
            backtrack(res, new ArrayList<>(), s, 0);
            return res;
        }
        private void backtrack(List<List<String>> res, List<String> current, String s, int start){
            if(start == s.length()){
                res.add(new ArrayList<>(current));
                return;
            }
            for(int end = start;end < s.length();end++){
                if(isPalindrome(s, start, end)){ //当前substring是回文，才继续往后扫描
                    current.add(s.substring(start, end + 1));
                    backtrack(res, current, s, end+1);
                    current.remove(current.size() - 1);
                }
            }
        }
        private boolean isPalindrome(String s, int start, int end){
            while(start < end){
                if(s.charAt(start) != s.charAt(end))
                    return false;
                start++;
                end--;
            }
            return true;
        }
    }

}
