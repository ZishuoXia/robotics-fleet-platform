package com.zishuo.fleet.common.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 控制器日志切面 - 给所有 @RestController 方法自动加上"进入/退出"日志和耗时统计。
 *
 * <h2>AOP 解决了什么</h2>
 * 没有 AOP 时,要在每个 controller 方法里手工写:
 * <pre>
 * long start = System.currentTimeMillis();
 * log.info("api called");
 * try { ... 业务 ... }
 * finally { log.info("took {}ms", System.currentTimeMillis() - start); }
 * </pre>
 * 几十个 controller 就要重复几十遍。AOP 让我们写一次,自动应用到所有 controller。
 *
 * <h2>对比中国传统项目的"操作日志写数据库"模式</h2>
 * 宝安项目通常会建一张 sys_log 表,每次操作写一行。这种做法已经过时:
 * <ul>
 *   <li>给 DB 增加无意义的写压力</li>
 *   <li>查询和分析麻烦</li>
 *   <li>结构化日志(本类) + ELK/Datadog 才是现代做法</li>
 * </ul>
 */
@Slf4j
@Aspect      // 声明这是个切面类
@Component   // 注册为 Spring bean,Spring 才会激活它
public class ControllerLoggingAspect {

    /**
     * 切点定义 - 描述"切面要应用在哪些方法上"。
     *
     * <p>{@code within(@org.springframework.web.bind.annotation.RestController *)} 的含义:
     * 任何标注了 @RestController 的类内的所有方法。
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restController() {
        // 切点方法的方法体永远是空的,只是用方法签名作为切点的"名字"
    }

    /**
     * 环绕通知 - 在目标方法执行前后插入逻辑。
     *
     * <p>ProceedingJoinPoint 表示"被切到的那个方法",调用 .proceed() 才会真正执行原方法。
     * 这给了我们机会在前后做事(打日志、计时)、改参数、甚至跳过原方法。
     */

    @Around("restController()")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        long start = System.currentTimeMillis();
        try {
            //被“切到”的方法，执行权已经被切面接管。只有调用 proceed()，原方法才会被执行。
            Object result = joinPoint.proceed(); // 这一行就是"执行真正的 controller 方法"
            long elapsed = System.currentTimeMillis() - start;
            log.info("API {} completed in {}ms", method, elapsed);
            return result;
        } catch (Throwable t) {
            // 即使方法抛异常也要记录耗时,然后把异常重新抛出去
            long elapsed = System.currentTimeMillis() - start;
            log.warn("API {} failed in {}ms: {}", method, elapsed, t.getMessage());
            throw t;
        }
    }
}
