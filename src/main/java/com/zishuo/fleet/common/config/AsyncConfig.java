package com.zishuo.fleet.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行的线程池配置。
 *
 * <h2>用途</h2>
 * 标了 {@code @Async} 的 Service 方法会在这个线程池上执行,不阻塞主调用线程。
 * 例如发送通知邮件、记录审计日志等"火忘"场景。
 *
 * <h2>当前参数(M1 阶段保守值,后续按需调优)</h2>
 * <ul>
 *   <li>核心线程 4:平时保留 4 个线程随时干活</li>
 *   <li>最大线程 16:高峰期最多扩到 16</li>
 *   <li>队列 100:线程满了之后排队 100 个,再多就触发拒绝策略</li>
 * </ul>
 *
 * <h2>触发条件</h2>
 * {@code @EnableAsync} 注解(在 FleetApplication 上)激活整个机制。
 */
@Configuration
public class AsyncConfig {

    /**
     * 定义名为 "taskExecutor" 的线程池 bean。
     * Spring 的 {@code @Async} 默认找名为 "taskExecutor" 的 bean,所以名字不要改。
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);          // 平时保留的线程数
        executor.setMaxPoolSize(16);          // 最大可扩展的线程数
        executor.setQueueCapacity(100);       // 任务队列容量
        executor.setThreadNamePrefix("fleet-async-"); // 日志里能看出哪个线程是异步线程
        executor.initialize();
        return executor;
    }
}
