package com.example.filetransfer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 异步执行配置类。
 * 负责创建文件传输线程池、协调线程池以及状态刷库线程池。
 */
@Configuration
@EnableAsync
@EnableScheduling
@RequiredArgsConstructor
@EnableConfigurationProperties(TransferProperties.class)
public class AsyncConfig {

    /** 全局迁移配置。 */
    private final TransferProperties transferProperties;

    /**
     * 创建真正执行文件复制的线程池。
     *
     * @return 文件传输线程池
     */
    @Bean(name = "transferExecutor")
    public ThreadPoolTaskExecutor transferExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 复制线程池的核心线程数和最大线程数保持一致，避免在运行中频繁扩缩容。
        executor.setCorePoolSize(transferProperties.getMaxConcurrency());
        executor.setMaxPoolSize(transferProperties.getMaxConcurrency());
        // 队列容量按并发度放大，既能缓冲短时突发，也避免无限堆积。
        executor.setQueueCapacity(Math.max(transferProperties.getMaxConcurrency() * 4, 16));
        executor.setThreadNamePrefix("transfer-");
        // 应用关闭时等待在途任务收尾，尽量减少中途中断。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        // 线程池启用前先确保断点续传临时目录已经就绪。
        ensureTempDir();
        return executor;
    }

    /**
     * 创建协调线程池。
     * 该线程池只负责轻量协调逻辑，不承担真正的大文件复制任务。
     *
     * @return 协调线程池
     */
    @Bean(name = "transferCoordinatorExecutor")
    public ThreadPoolTaskExecutor transferCoordinatorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 协调线程池规模固定较小，避免与真正的数据传输线程竞争资源。
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("transfer-coordinator-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 创建文件状态异步刷库线程池。
     *
     * @return 状态刷库线程池
     */
    @Bean(name = "stateFlushExecutor")
    public ThreadPoolTaskExecutor stateFlushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 状态刷库是轻量但频繁的后台动作，独立线程池可避免阻塞主执行流程。
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("state-flush-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 确保断点续传临时目录存在。
     */
    private void ensureTempDir() {
        try {
            Path path = Paths.get(transferProperties.getTempDir());
            // 如果目录已经存在则不会报错；不存在时会自动创建完整目录层级。
            Files.createDirectories(path);
        } catch (Exception ex) {
            // 临时目录不可用会直接影响续传能力，因此启动阶段就快速失败。
            throw new IllegalStateException("Failed to initialize temp directory", ex);
        }
    }
}
