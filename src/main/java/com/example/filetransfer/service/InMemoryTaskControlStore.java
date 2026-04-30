package com.example.filetransfer.service;

import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于内存的任务控制信号存储。
 * 作为未启用 Redis 时的默认回退实现。
 */
@Service
@ConditionalOnProperty(prefix = "transfer", name = "redis-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryTaskControlStore implements TaskControlStore {

    private final ConcurrentHashMap<String, TaskControlState> states = new ConcurrentHashMap<>();

    @Override
    public void requestPause(String taskId) {
        states.computeIfAbsent(taskId, key -> new TaskControlState()).getPauseRequested().set(true);
    }

    @Override
    public void requestCancel(String taskId) {
        TaskControlState state = states.computeIfAbsent(taskId, key -> new TaskControlState());
        state.getCancelRequested().set(true);
        state.getPauseRequested().set(false);
    }

    @Override
    public void clearSignals(String taskId) {
        states.remove(taskId);
    }

    @Override
    public boolean isPauseRequested(String taskId) {
        TaskControlState state = states.get(taskId);
        return state != null && state.getPauseRequested().get() && !state.getCancelRequested().get();
    }

    @Override
    public boolean isCancelRequested(String taskId) {
        TaskControlState state = states.get(taskId);
        return state != null && state.getCancelRequested().get();
    }

    @Override
    public boolean peekPauseRequested(String taskId) {
        TaskControlState state = states.get(taskId);
        return state != null && state.getPauseRequested().get();
    }

    @Override
    public boolean peekCancelRequested(String taskId) {
        TaskControlState state = states.get(taskId);
        return state != null && state.getCancelRequested().get();
    }

    @Getter
    private static class TaskControlState {
        private final AtomicBoolean pauseRequested = new AtomicBoolean(false);
        private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    }
}
