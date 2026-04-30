package com.example.filetransfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 超大规模迁移任务控制服务。
 * 对外暴露暂停、恢复、取消等控制能力，底层可接内存或 Redis。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScalableTaskControlService {

    private final TaskControlStore taskControlStore;

    /**
     * 请求暂停任务。
     *
     * @param taskId 任务 ID
     */
    public void requestPause(String taskId) {
        log.debug("设置暂停信号, taskId={}", taskId);
        taskControlStore.requestPause(taskId);
    }

    /**
     * 请求取消任务。
     *
     * @param taskId 任务 ID
     */
    public void requestCancel(String taskId) {
        log.debug("设置取消信号, taskId={}", taskId);
        taskControlStore.requestCancel(taskId);
    }

    /**
     * 清理历史控制信号。
     *
     * @param taskId 任务 ID
     */
    public void clearSignals(String taskId) {
        log.debug("清理控制信号, taskId={}", taskId);
        taskControlStore.clearSignals(taskId);
    }

    /**
     * 判断是否已请求暂停。
     *
     * @param taskId 任务 ID
     * @return 是否已暂停
     */
    public boolean isPauseRequested(String taskId) {
        return taskControlStore.isPauseRequested(taskId);
    }

    /**
     * 判断是否已请求取消。
     *
     * @param taskId 任务 ID
     * @return 是否已取消
     */
    public boolean isCancelRequested(String taskId) {
        return taskControlStore.isCancelRequested(taskId);
    }

    /**
     * 查看暂停信号快照。
     *
     * @param taskId 任务 ID
     * @return 暂停信号快照
     */
    public boolean peekPauseRequested(String taskId) {
        return taskControlStore.peekPauseRequested(taskId);
    }

    /**
     * 查看取消信号快照。
     *
     * @param taskId 任务 ID
     * @return 取消信号快照
     */
    public boolean peekCancelRequested(String taskId) {
        return taskControlStore.peekCancelRequested(taskId);
    }
}
