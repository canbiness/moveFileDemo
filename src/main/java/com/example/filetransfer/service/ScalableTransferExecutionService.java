package com.example.filetransfer.service;

import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.TransferTask;
import com.example.filetransfer.dto.ScalableTransferActionResponse;
import com.example.filetransfer.exception.TransferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 任务执行动作门面服务。
 * 负责处理 execute、pause、resume、cancel 等用户触发的动作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScalableTransferExecutionService {

    /** 任务冷状态持久化服务。 */
    private final StatePersistenceService statePersistenceService;

    /** 真正执行任务的后台工作器。 */
    private final ScalableTransferExecutionWorker executionWorker;

    /** 任务控制信号服务。 */
    private final ScalableTaskControlService scalableTaskControlService;

    /** 每个任务对应的互斥锁，避免重复执行。 */
    private final ConcurrentHashMap<String, ReentrantLock> taskLocks = new ConcurrentHashMap<>();

    /**
     * 如果任务处于可执行状态且当前没有其他执行轮次持有锁，则调度后台执行。
     *
     * @param taskId 任务 ID
     * @return 执行动作响应
     */
    public ScalableTransferActionResponse execute(String taskId) {
        TransferTask task = statePersistenceService.getTask(taskId);
        log.info("Received execute request, taskId={}, status={}", taskId, task.getStatus());
        if (task.getStatus() == TransferStatus.COMPLETED || task.getStatus() == TransferStatus.CANCELED) {
            throw new TransferException("Task cannot be executed in current status: " + task.getStatus());
        }
        if (task.getStatus() != TransferStatus.PLANNED
                && task.getStatus() != TransferStatus.FAILED
                && task.getStatus() != TransferStatus.PAUSED
                && task.getStatus() != TransferStatus.RUNNING) {
            throw new TransferException("Task is not ready for execution: " + task.getStatus());
        }

        ReentrantLock lock = taskLocks.computeIfAbsent(taskId, key -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new TransferException("Task is already executing: " + taskId);
        }

        try {
            scalableTaskControlService.clearSignals(taskId);
            CompletableFuture<Void> future = executionWorker.executeAsync(taskId, lock);
            future.whenComplete((ignored, error) -> taskLocks.remove(taskId, lock));
            return new ScalableTransferActionResponse(taskId, TransferStatus.RUNNING, "Execution scheduled");
        } catch (RuntimeException ex) {
            lock.unlock();
            taskLocks.remove(taskId, lock);
            throw ex;
        }
    }

    /**
     * 请求协作式暂停，后台工作器会在安全检查点观察到该信号。
     *
     * @param taskId 任务 ID
     * @return 暂停动作响应
     */
    public ScalableTransferActionResponse pause(String taskId) {
        TransferTask task = statePersistenceService.getTask(taskId);
        log.info("Received pause request, taskId={}, status={}", taskId, task.getStatus());
        if (task.getStatus() == TransferStatus.CREATED || task.getStatus() == TransferStatus.PLANNING) {
            throw new TransferException("Task cannot be paused while planning is still in progress: " + task.getStatus());
        }
        if (task.getStatus() == TransferStatus.COMPLETED || task.getStatus() == TransferStatus.CANCELED) {
            throw new TransferException("Task cannot be paused in current status: " + task.getStatus());
        }
        scalableTaskControlService.requestPause(taskId);
        statePersistenceService.updateTaskStatus(taskId, TransferStatus.PAUSED, null);
        return new ScalableTransferActionResponse(taskId, TransferStatus.PAUSED, "Pause requested");
    }

    /**
     * 恢复一个已暂停或失败的任务，内部复用标准执行入口。
     *
     * @param taskId 任务 ID
     * @return 恢复动作响应
     */
    public ScalableTransferActionResponse resume(String taskId) {
        TransferTask task = statePersistenceService.getTask(taskId);
        log.info("Received resume request, taskId={}, status={}", taskId, task.getStatus());
        if (task.getStatus() != TransferStatus.PAUSED && task.getStatus() != TransferStatus.FAILED) {
            throw new TransferException("Task cannot be resumed in current status: " + task.getStatus());
        }
        return execute(taskId);
    }

    /**
     * 请求协作式取消，后台工作器会在安全检查点观察到该信号。
     *
     * @param taskId 任务 ID
     * @return 取消动作响应
     */
    public ScalableTransferActionResponse cancel(String taskId) {
        TransferTask task = statePersistenceService.getTask(taskId);
        log.info("Received cancel request, taskId={}, status={}", taskId, task.getStatus());
        if (task.getStatus() == TransferStatus.CREATED || task.getStatus() == TransferStatus.PLANNING) {
            throw new TransferException("Task cannot be canceled while planning is still in progress: " + task.getStatus());
        }
        if (task.getStatus() == TransferStatus.COMPLETED || task.getStatus() == TransferStatus.CANCELED) {
            throw new TransferException("Task cannot be canceled in current status: " + task.getStatus());
        }
        scalableTaskControlService.requestCancel(taskId);
        statePersistenceService.updateTaskStatus(taskId, TransferStatus.CANCELED, null);
        return new ScalableTransferActionResponse(taskId, TransferStatus.CANCELED, "Cancel requested");
    }
}
