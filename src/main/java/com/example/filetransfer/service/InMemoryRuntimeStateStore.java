package com.example.filetransfer.service;

import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.ThrottleReason;
import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于内存的运行态热状态存储。
 * 作为未启用 Redis 时的默认回退实现。
 */
@Service
@ConditionalOnProperty(prefix = "transfer", name = "redis-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRuntimeStateStore implements RuntimeStateStore {

    private final ConcurrentHashMap<String, TaskRuntimeState> states = new ConcurrentHashMap<>();

    @Override
    public void markTaskStarted(String taskId, String schedulingStrategy) {
        TaskRuntimeState state = states.computeIfAbsent(taskId, key -> new TaskRuntimeState());
        state.setSchedulingStrategy(schedulingStrategy);
        state.getBackpressureActive().set(false);
    }

    @Override
    public void markTaskFinished(String taskId) {
        TaskRuntimeState state = states.get(taskId);
        if (state != null) {
            state.getBackpressureActive().set(false);
        }
    }

    @Override
    public void onBatchStarted(String taskId, BatchTemperature temperature, long batchBytes) {
        TaskRuntimeState state = states.computeIfAbsent(taskId, key -> new TaskRuntimeState());
        state.getInFlightBatches().incrementAndGet();
        state.getInFlightBytes().addAndGet(batchBytes);
        state.getInFlightByTemperature().get(temperature).incrementAndGet();
    }

    @Override
    public void onBatchFinished(String taskId, BatchTemperature temperature, long batchBytes) {
        TaskRuntimeState state = states.get(taskId);
        if (state == null) {
            return;
        }
        state.getInFlightBatches().decrementAndGet();
        state.getInFlightBytes().addAndGet(-batchBytes);
        state.getInFlightByTemperature().get(temperature).decrementAndGet();
    }

    @Override
    public void onFileStarted(String taskId) {
        states.computeIfAbsent(taskId, key -> new TaskRuntimeState()).getInFlightFiles().incrementAndGet();
    }

    @Override
    public void onFileFinished(String taskId) {
        TaskRuntimeState state = states.get(taskId);
        if (state != null) {
            state.getInFlightFiles().decrementAndGet();
        }
    }

    @Override
    public void onRetry(String taskId) {
        states.computeIfAbsent(taskId, key -> new TaskRuntimeState()).getRetryCount().incrementAndGet();
    }

    @Override
    public void onThrottle(String taskId, Map<ThrottleReason, Boolean> reasons) {
        TaskRuntimeState state = states.computeIfAbsent(taskId, key -> new TaskRuntimeState());
        state.getThrottledDispatchCount().incrementAndGet();
        state.getBackpressureActive().set(true);
        reasons.forEach((reason, active) -> {
            if (Boolean.TRUE.equals(active)) {
                state.getThrottleReasonCounts().get(reason).incrementAndGet();
            }
        });
    }

    @Override
    public void clearThrottle(String taskId) {
        TaskRuntimeState state = states.get(taskId);
        if (state != null) {
            state.getBackpressureActive().set(false);
        }
    }

    @Override
    public ScalableRuntimeMonitorService.RuntimeSnapshot snapshot(String taskId) {
        TaskRuntimeState state = states.get(taskId);
        if (state == null) {
            return ScalableRuntimeMonitorService.RuntimeSnapshot.empty();
        }
        Map<BatchTemperature, Long> temperatureMap = new EnumMap<>(BatchTemperature.class);
        for (BatchTemperature temperature : BatchTemperature.values()) {
            temperatureMap.put(temperature, state.getInFlightByTemperature().get(temperature).get());
        }
        Map<ThrottleReason, Long> throttleReasonMap = new EnumMap<>(ThrottleReason.class);
        for (ThrottleReason reason : ThrottleReason.values()) {
            throttleReasonMap.put(reason, state.getThrottleReasonCounts().get(reason).get());
        }
        return new ScalableRuntimeMonitorService.RuntimeSnapshot(
                state.getSchedulingStrategy(),
                state.getBackpressureActive().get(),
                state.getInFlightBatches().get(),
                state.getInFlightFiles().get(),
                state.getInFlightBytes().get(),
                state.getRetryCount().get(),
                state.getThrottledDispatchCount().get(),
                temperatureMap,
                throttleReasonMap
        );
    }

    @Getter
    private static class TaskRuntimeState {
        private final AtomicLong inFlightBatches = new AtomicLong();
        private final AtomicLong inFlightFiles = new AtomicLong();
        private final AtomicLong inFlightBytes = new AtomicLong();
        private final AtomicLong retryCount = new AtomicLong();
        private final AtomicLong throttledDispatchCount = new AtomicLong();
        private final AtomicBoolean backpressureActive = new AtomicBoolean(false);
        private final EnumMap<BatchTemperature, AtomicLong> inFlightByTemperature = new EnumMap<>(BatchTemperature.class);
        private final EnumMap<ThrottleReason, AtomicLong> throttleReasonCounts = new EnumMap<>(ThrottleReason.class);
        private String schedulingStrategy = "temperature-priority-backpressure";

        private TaskRuntimeState() {
            for (BatchTemperature temperature : BatchTemperature.values()) {
                inFlightByTemperature.put(temperature, new AtomicLong());
            }
            for (ThrottleReason reason : ThrottleReason.values()) {
                throttleReasonCounts.put(reason, new AtomicLong());
            }
        }

        private void setSchedulingStrategy(String schedulingStrategy) {
            this.schedulingStrategy = schedulingStrategy;
        }
    }
}
