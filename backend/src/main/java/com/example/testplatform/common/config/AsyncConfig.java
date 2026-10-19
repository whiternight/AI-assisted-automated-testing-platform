package com.example.testplatform.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 *
 * @author TestPlatform
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(AsyncConfig.AsyncThreadPoolProperties.class)
public class AsyncConfig {

    /**
     * 创建异步任务执行器
     *
     * @param properties 线程池配置
     * @return Executor
     */
    @Bean("asyncTaskExecutor")
    public Executor taskExecutor(AsyncThreadPoolProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数
        executor.setCorePoolSize(properties.getCorePoolSize());

        // 最大线程数
        executor.setMaxPoolSize(properties.getMaxPoolSize());

        // 队列容量
        executor.setQueueCapacity(properties.getQueueCapacity());

        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());

        // 线程名称前缀
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());

        // 拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 等待时间（秒）
        executor.setAwaitTerminationSeconds(60);

        // 初始化
        executor.initialize();

        return executor;
    }

    /**
     * 异步线程池配置属性
     */
    @Data
    @ConfigurationProperties(prefix = "async.thread-pool")
    public static class AsyncThreadPoolProperties {
        /**
         * 核心线程数
         */
        private int corePoolSize = 10;

        /**
         * 最大线程数
         */
        private int maxPoolSize = 50;

        /**
         * 队列容量
         */
        private int queueCapacity = 200;

        /**
         * 线程空闲时间（秒）
         */
        private int keepAliveSeconds = 60;

        /**
         * 线程名称前缀
         */
        private String threadNamePrefix = "async-";
    }
}
