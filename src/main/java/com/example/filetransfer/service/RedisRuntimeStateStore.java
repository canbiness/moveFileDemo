package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.ThrottleReason;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * 基于 Redis Hash 的运行态热状态存储。
 * 适合多实例共享热监控状态。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "transfer", name = "redis-enabled", havingValue = "true")
public class RedisRuntimeStateStore implements RuntimeStateStore {

    private static final String FIELD_STRATEGY = "strategy";
    private static final String FIELD_BACKPRESSURE_ACTIVE = "backpressureActive";
    private static final String FIELD_IN_FLIGHT_BATCHES = "inFlightBatches";
    private static final String FIELD_IN_FLIGHT_FILES = "inFlightFiles";
    private static final String FIELD_IN_FLIGHT_BYTES = "inFlightBytes";
    private static final String FIELD_RETRY_COUNT = "retryCount";
    private static final String FIELD_THROTTLED_COUNT = "throttledDispatchCount";

    private final StringRedisTemplate stringRedisTemplate;
    private final TransferProperties transferProperties;

    @Override
    public void markTaskStarted(String taskId, String schedulingStrategy) {
        String key = key(taskId);
        HashOperations<String, Object, Object> hash = stringRedisTemplate.opsForHash();
        hash.put(key, FIELD_STRATEGY, schedulingStrategy);
        hash.put(key, FIELD_BACKPRESSURE_ACTIVE, Boolean.FALSE.toString());
        touch(key);
    }

    @Override
    public void markTaskFinished(String taskId) {
        String key = key(taskId);
        stringRedisTemplate.opsForHash().put(key, FIELD_BACKPRESSURE_ACTIVE, Boolean.FALSE.toString());
        touch(key);
    }

    @Override
    public void onBatchStarted(String taskId, BatchTemperature temperature, long batchBytes) {
        String key = key(taskId);
        HashOperations<String, Object, Object> hash = stringRedisTemplate.opsForHash();
        hash.increment(key, FIELD_IN_FLIGHT_BATCHES, 1L);
        hash.increment(key, FIELD_IN_FLIGHT_BYTES, batchBytes);
        hash.increment(key, temperatureField(temperature), 1L);
        touch(key);
    }

    @Override
    public void onBatchFinished(String taskId, BatchTemperature temperature, long batchBytes) {
        String key = key(taskId);
        HashOperations<String, Object, Object> hash = stringRedisTemplate.opsForHash();
        hash.increment(key, FIELD_IN_FLIGHT_BATCHES, -1L);
        hash.increment(key, FIELD_IN_FLIGHT_BYTES, -batchBytes);
        hash.increment(key, temperatureField(temperature), -1L);
        touch(key);
    }

    @Override
    public void onFileStarted(String taskId) {
        String key = key(taskId);
        stringRedisTemplate.opsForHash().increment(key, FIELD_IN_FLIGHT_FILES, 1L);
        touch(key);
    }

    @Override
    public void onFileFinished(String taskId) {
        String key = key(taskId);
        stringRedisTemplate.opsForHash().increment(key, FIELD_IN_FLIGHT_FILES, -1L);
        touch(key);
    }

    @Override
    public void onRetry(String taskId) {
        String key = key(taskId);
        stringRedisTemplate.opsForHash().increment(key, FIELD_RETRY_COUNT, 1L);
        touch(key);
    }

    @Override
    public void onThrottle(String taskId, Map<ThrottleReason, Boolean> reasons) {
        String key = key(taskId);
        HashOperations<String, Object, Object> hash = stringRedisTemplate.opsForHash();
        hash.increment(key, FIELD_THROTTLED_COUNT, 1L);
        hash.put(key, FIELD_BACKPRESSURE_ACTIVE, Boolean.TRUE.toString());
        reasons.forEach((reason, active) -> {
            if (Boolean.TRUE.equals(active)) {
                hash.increment(key, throttleField(reason), 1L);
            }
        });
        touch(key);
    }

    @Override
    public void clearThrottle(String taskId) {
        String key = key(taskId);
        stringRedisTemplate.opsForHash().put(key, FIELD_BACKPRESSURE_ACTIVE, Boolean.FALSE.toString());
        touch(key);
    }

    @Override
    public ScalableRuntimeMonitorService.RuntimeSnapshot snapshot(String taskId) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(key(taskId));
        if (values.isEmpty()) {
            return ScalableRuntimeMonitorService.RuntimeSnapshot.empty();
        }
        EnumMap<BatchTemperature, Long> temperatureCounts = new EnumMap<>(BatchTemperature.class);
        for (BatchTemperature temperature : BatchTemperature.values()) {
            temperatureCounts.put(temperature, readLong(values, temperatureField(temperature)));
        }
        EnumMap<ThrottleReason, Long> throttleCounts = new EnumMap<>(ThrottleReason.class);
        for (ThrottleReason reason : ThrottleReason.values()) {
            throttleCounts.put(reason, readLong(values, throttleField(reason)));
        }
        return new ScalableRuntimeMonitorService.RuntimeSnapshot(
                readString(values, FIELD_STRATEGY, "temperature-priority-backpressure"),
                Boolean.parseBoolean(readString(values, FIELD_BACKPRESSURE_ACTIVE, Boolean.FALSE.toString())),
                readLong(values, FIELD_IN_FLIGHT_BATCHES),
                readLong(values, FIELD_IN_FLIGHT_FILES),
                readLong(values, FIELD_IN_FLIGHT_BYTES),
                readLong(values, FIELD_RETRY_COUNT),
                readLong(values, FIELD_THROTTLED_COUNT),
                temperatureCounts,
                throttleCounts
        );
    }

    private String key(String taskId) {
        return transferProperties.getRedisKeyPrefix() + ":runtime:" + taskId;
    }

    private String temperatureField(BatchTemperature temperature) {
        return "temperature:" + temperature.name();
    }

    private String throttleField(ThrottleReason reason) {
        return "throttle:" + reason.name();
    }

    private void touch(String key) {
        stringRedisTemplate.expire(key, Duration.ofSeconds(transferProperties.getRedisStateTtlSeconds()));
    }

    private long readLong(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private String readString(Map<Object, Object> values, String field, String defaultValue) {
        Object value = values.get(field);
        return value == null ? defaultValue : value.toString();
    }
}
