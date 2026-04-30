package com.example.filetransfer.service;

import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.ThrottleReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * 超大规模任务运行态监控服务。
 * 对外暴露统一的监控操作，底层可接内存或 Redis 热状态存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScalableRuntimeMonitorService {

    private final RuntimeStateStore runtimeStateStore;

    public void markTaskStarted(String taskId, String schedulingStrategy) {
        log.debug("初始化任务运行态监控, taskId={}, strategy={}", taskId, schedulingStrategy);
        runtimeStateStore.markTaskStarted(taskId, schedulingStrategy);
    }

    public void markTaskFinished(String taskId) {
        log.debug("任务运行态监控标记结束, taskId={}", taskId);
        runtimeStateStore.markTaskFinished(taskId);
    }

    public void onBatchStarted(String taskId, BatchTemperature temperature, long batchBytes) {
        runtimeStateStore.onBatchStarted(taskId, temperature, batchBytes);
    }

    public void onBatchFinished(String taskId, BatchTemperature temperature, long batchBytes) {
        runtimeStateStore.onBatchFinished(taskId, temperature, batchBytes);
    }

    public void onFileStarted(String taskId) {
        runtimeStateStore.onFileStarted(taskId);
    }

    public void onFileFinished(String taskId) {
        runtimeStateStore.onFileFinished(taskId);
    }

    public void onRetry(String taskId) {
        runtimeStateStore.onRetry(taskId);
    }

    public void onThrottle(String taskId, Map<ThrottleReason, Boolean> reasons) {
        runtimeStateStore.onThrottle(taskId, reasons);
    }

    public void clearThrottle(String taskId) {
        runtimeStateStore.clearThrottle(taskId);
    }

    public RuntimeSnapshot snapshot(String taskId) {
        return runtimeStateStore.snapshot(taskId);
    }

    /**
     * 运行态快照。
     *
     * @param schedulingStrategy       调度策略名称
     * @param backpressureActive       当前是否处于背压状态
     * @param inFlightBatches          当前在途批次数
     * @param inFlightFiles            当前在途文件数
     * @param inFlightBytes            当前在途字节数
     * @param retryCount               当前累计重试次数
     * @param throttledDispatchCount   当前累计背压次数
     * @param inFlightTemperatureCounts 当前在途批次冷热分层统计
     * @param throttleReasonCounts     当前背压原因统计
     */
    public record RuntimeSnapshot(String schedulingStrategy,
                                  boolean backpressureActive,
                                  long inFlightBatches,
                                  long inFlightFiles,
                                  long inFlightBytes,
                                  long retryCount,
                                  long throttledDispatchCount,
                                  Map<BatchTemperature, Long> inFlightTemperatureCounts,
                                  Map<ThrottleReason, Long> throttleReasonCounts) {
        static RuntimeSnapshot empty() {
            Map<BatchTemperature, Long> temperatureMap = new EnumMap<>(BatchTemperature.class);
            for (BatchTemperature temperature : BatchTemperature.values()) {
                temperatureMap.put(temperature, 0L);
            }
            Map<ThrottleReason, Long> throttleReasonMap = new EnumMap<>(ThrottleReason.class);
            for (ThrottleReason reason : ThrottleReason.values()) {
                throttleReasonMap.put(reason, 0L);
            }
            return new RuntimeSnapshot(
                    "temperature-priority-backpressure",
                    false,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    temperatureMap,
                    throttleReasonMap
            );
        }
    }
}
