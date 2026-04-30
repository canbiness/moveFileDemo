package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * 基于 Redis 的任务控制信号存储。
 * 适合多实例部署时共享暂停、取消等热控制状态。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "transfer", name = "redis-enabled", havingValue = "true")
public class RedisTaskControlStore implements TaskControlStore {

    private static final String FIELD_PAUSE = "pauseRequested";
    private static final String FIELD_CANCEL = "cancelRequested";

    private final StringRedisTemplate stringRedisTemplate;
    private final TransferProperties transferProperties;

    @Override
    public void requestPause(String taskId) {
        String key = key(taskId);
        stringRedisTemplate.opsForHash().putAll(key, Map.of(
                FIELD_PAUSE, Boolean.TRUE.toString(),
                FIELD_CANCEL, Boolean.FALSE.toString()
        ));
        touch(key);
    }

    @Override
    public void requestCancel(String taskId) {
        String key = key(taskId);
        stringRedisTemplate.opsForHash().putAll(key, Map.of(
                FIELD_PAUSE, Boolean.FALSE.toString(),
                FIELD_CANCEL, Boolean.TRUE.toString()
        ));
        touch(key);
    }

    @Override
    public void clearSignals(String taskId) {
        stringRedisTemplate.delete(key(taskId));
    }

    @Override
    public boolean isPauseRequested(String taskId) {
        String key = key(taskId);
        boolean pauseRequested = Boolean.parseBoolean((String) stringRedisTemplate.opsForHash().get(key, FIELD_PAUSE));
        boolean cancelRequested = Boolean.parseBoolean((String) stringRedisTemplate.opsForHash().get(key, FIELD_CANCEL));
        return pauseRequested && !cancelRequested;
    }

    @Override
    public boolean isCancelRequested(String taskId) {
        return Boolean.parseBoolean((String) stringRedisTemplate.opsForHash().get(key(taskId), FIELD_CANCEL));
    }

    @Override
    public boolean peekPauseRequested(String taskId) {
        return Boolean.parseBoolean((String) stringRedisTemplate.opsForHash().get(key(taskId), FIELD_PAUSE));
    }

    @Override
    public boolean peekCancelRequested(String taskId) {
        return Boolean.parseBoolean((String) stringRedisTemplate.opsForHash().get(key(taskId), FIELD_CANCEL));
    }

    private String key(String taskId) {
        return transferProperties.getRedisKeyPrefix() + ":control:" + taskId;
    }

    private void touch(String key) {
        stringRedisTemplate.expire(key, Duration.ofSeconds(transferProperties.getRedisStateTtlSeconds()));
    }
}
