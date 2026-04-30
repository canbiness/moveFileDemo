package com.example.filetransfer.dto;

/**
 * 线程池运行指标快照。
 *
 * @param corePoolSize 核心线程数
 * @param maxPoolSize 最大线程数
 * @param poolSize 当前线程池中的线程总数
 * @param activeCount 当前活跃线程数
 * @param queueSize 当前队列中等待执行的任务数
 * @param remainingQueueCapacity 队列剩余容量
 * @param completedTaskCount 已完成任务总数
 * @param taskCount 历史提交任务总数
 */
public record ExecutorMetricsResponse(
        int corePoolSize,
        int maxPoolSize,
        int poolSize,
        int activeCount,
        int queueSize,
        int remainingQueueCapacity,
        long completedTaskCount,
        long taskCount
) {
}
