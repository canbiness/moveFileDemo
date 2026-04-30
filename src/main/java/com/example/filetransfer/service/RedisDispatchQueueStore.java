package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.TransferBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis Sorted Set 的调度热队列实现。
 * 每个任务按温度维度拆成多个有序热队列。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "transfer", name = "redis-enabled", havingValue = "true")
public class RedisDispatchQueueStore implements DispatchQueueStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final TransferProperties transferProperties;

    @Override
    public void clearTask(String taskId) {
        for (BatchTemperature temperature : BatchTemperature.values()) {
            stringRedisTemplate.delete(queueKey(taskId, temperature));
        }
    }

    @Override
    public void enqueueBatch(String taskId, TransferBatch batch) {
        String key = queueKey(taskId, batch.getTemperatureTier());
        double score = buildScore(batch.getSchedulingPriority(), batch.getBatchNumber());
        stringRedisTemplate.opsForZSet().add(key, batch.getId().toString(), score);
        stringRedisTemplate.expire(key, Duration.ofSeconds(transferProperties.getRedisStateTtlSeconds()));
    }

    @Override
    public List<Long> pollBatchIds(String taskId, int limit, int hotDispatch, int warmDispatch, int coldDispatch) {
        List<Long> ids = new ArrayList<>(limit);
        EnumMap<BatchTemperature, Integer> burstMap = new EnumMap<>(BatchTemperature.class);
        burstMap.put(BatchTemperature.HOT, hotDispatch);
        burstMap.put(BatchTemperature.WARM, warmDispatch);
        burstMap.put(BatchTemperature.COLD, coldDispatch);

        while (ids.size() < limit) {
            int before = ids.size();
            for (BatchTemperature temperature : BatchTemperature.values()) {
                drainQueue(taskId, temperature, burstMap.get(temperature), limit, ids);
            }
            if (ids.size() == before) {
                break;
            }
        }
        return ids;
    }

    private void drainQueue(String taskId, BatchTemperature temperature, int count, int limit, List<Long> ids) {
        if (count <= 0 || ids.size() >= limit) {
            return;
        }
        String key = queueKey(taskId, temperature);
        long endIndex = Math.max(0, Math.min(count, limit - ids.size()) - 1L);
        Set<String> members = stringRedisTemplate.opsForZSet().range(key, 0, endIndex);
        if (members == null || members.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(key, members.toArray());
        for (String member : members) {
            ids.add(Long.parseLong(member));
        }
    }

    private String queueKey(String taskId, BatchTemperature temperature) {
        return transferProperties.getRedisKeyPrefix() + ":dispatch:" + taskId + ":" + temperature.name();
    }

    private double buildScore(int schedulingPriority, int batchNumber) {
        return schedulingPriority * 1_000_000D + batchNumber;
    }
}
