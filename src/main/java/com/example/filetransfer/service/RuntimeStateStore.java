package com.example.filetransfer.service;

import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.ThrottleReason;

import java.util.Map;

/**
 * 运行态热状态存储接口。
 * 用于承载监控接口依赖的细粒度热数据，可由内存或 Redis 实现。
 */
public interface RuntimeStateStore {

    /**
     * 标记任务开始执行。
     *
     * @param taskId              任务 ID
     * @param schedulingStrategy  调度策略名称
     */
    void markTaskStarted(String taskId, String schedulingStrategy);

    /**
     * 标记任务执行结束。
     *
     * @param taskId 任务 ID
     */
    void markTaskFinished(String taskId);

    /**
     * 记录批次开始。
     *
     * @param taskId      任务 ID
     * @param temperature 批次冷热标签
     * @param batchBytes  批次总字节数
     */
    void onBatchStarted(String taskId, BatchTemperature temperature, long batchBytes);

    /**
     * 记录批次结束。
     *
     * @param taskId      任务 ID
     * @param temperature 批次冷热标签
     * @param batchBytes  批次总字节数
     */
    void onBatchFinished(String taskId, BatchTemperature temperature, long batchBytes);

    /**
     * 记录文件开始。
     *
     * @param taskId 任务 ID
     */
    void onFileStarted(String taskId);

    /**
     * 记录文件结束。
     *
     * @param taskId 任务 ID
     */
    void onFileFinished(String taskId);

    /**
     * 记录重试次数。
     *
     * @param taskId 任务 ID
     */
    void onRetry(String taskId);

    /**
     * 记录背压触发。
     *
     * @param taskId  任务 ID
     * @param reasons 背压原因
     */
    void onThrottle(String taskId, Map<ThrottleReason, Boolean> reasons);

    /**
     * 清理背压状态。
     *
     * @param taskId 任务 ID
     */
    void clearThrottle(String taskId);

    /**
     * 获取运行态快照。
     *
     * @param taskId 任务 ID
     * @return 运行态快照
     */
    ScalableRuntimeMonitorService.RuntimeSnapshot snapshot(String taskId);
}
