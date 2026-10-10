package com.atguigu.tingshu.common.thread;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class ThreadPoolConfig {

    /**
     * 业务异步线程池
     * 使用方式：@Async("taskExecutor")，配合启动类/配置类的 @EnableAsync
     */
    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数（常驻线程）
        executor.setCorePoolSize(5);
        // 最大线程数
        executor.setMaxPoolSize(20);
        // 阻塞队列容量（有界，防止任务堆积导致 OOM）
        executor.setQueueCapacity(200);
        // 空闲线程存活时间（秒）
        executor.setKeepAliveSeconds(120);
        // 线程名前缀，便于排查日志
        executor.setThreadNamePrefix("sync-tingshu-Executor--");
        // 拒绝策略：队列满且线程满时，交给调用线程执行（降级不丢任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 关闭时等待任务执行完再停，最长等待 60 秒
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // 初始化线程池（必须调用，否则配置不生效）
        executor.initialize();
        return executor;
    }
}
