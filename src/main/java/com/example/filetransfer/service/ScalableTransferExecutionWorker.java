package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.domain.BatchStatus;
import com.example.filetransfer.domain.FileTransferStatus;
import com.example.filetransfer.domain.ScalableFileRecord;
import com.example.filetransfer.domain.TransferBatch;
import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.TransferTask;
import com.example.filetransfer.exception.TaskCancelRequestedException;
import com.example.filetransfer.exception.TaskPauseRequestedException;
import com.example.filetransfer.exception.TransferException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.filetransfer.mapper.ScalableFileRecordMapper;
import com.example.filetransfer.mapper.TransferBatchMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 执行已规划迁移任务的后台工作器。
 * 负责从持久化批次重建派发队列、执行背压控制，并支持重试、暂停和取消。
 */
@Slf4j
@Service
public class ScalableTransferExecutionWorker {

    private final TransferProperties transferProperties;
    private final StatePersistenceService statePersistenceService;
    private final ScalableExecutionStateService executionStateService;
    private final ScalableProgressService scalableProgressService;
    private final ScalableFileCopyService scalableFileCopyService;
    private final ScalableTaskControlService scalableTaskControlService;
    private final ScalableRuntimeMonitorService scalableRuntimeMonitorService;
    private final ScalableSchedulingService scalableSchedulingService;
    private final DispatchQueueStore dispatchQueueStore;
    private final TransferBatchMapper transferBatchMapper;
    private final ScalableFileRecordMapper scalableFileRecordMapper;
    private final ThreadPoolTaskExecutor transferExecutor;

    public ScalableTransferExecutionWorker(TransferProperties transferProperties,
                                           StatePersistenceService statePersistenceService,
                                           ScalableExecutionStateService executionStateService,
                                           ScalableProgressService scalableProgressService,
                                           ScalableFileCopyService scalableFileCopyService,
                                           ScalableTaskControlService scalableTaskControlService,
                                           ScalableRuntimeMonitorService scalableRuntimeMonitorService,
                                           ScalableSchedulingService scalableSchedulingService,
                                           DispatchQueueStore dispatchQueueStore,
                                           TransferBatchMapper transferBatchMapper,
                                           ScalableFileRecordMapper scalableFileRecordMapper,
                                           @Qualifier("transferExecutor") ThreadPoolTaskExecutor transferExecutor) {
        this.transferProperties = transferProperties;
        this.statePersistenceService = statePersistenceService;
        this.executionStateService = executionStateService;
        this.scalableProgressService = scalableProgressService;
        this.scalableFileCopyService = scalableFileCopyService;
        this.scalableTaskControlService = scalableTaskControlService;
        this.scalableRuntimeMonitorService = scalableRuntimeMonitorService;
        this.scalableSchedulingService = scalableSchedulingService;
        this.dispatchQueueStore = dispatchQueueStore;
        this.transferBatchMapper = transferBatchMapper;
        this.scalableFileRecordMapper = scalableFileRecordMapper;
        this.transferExecutor = transferExecutor;
    }

    /**
     * 在协调线程池中启动执行，让接口请求可以立即返回。
     */
    @Async("transferCoordinatorExecutor")
    public CompletableFuture<Void> executeAsync(String taskId, ReentrantLock lock) {
        log.info("Starting transfer execution, taskId={}", taskId);
        scalableRuntimeMonitorService.markTaskStarted(taskId, "temperature-priority-backpressure");
        try {
            runExecution(taskId);
            log.info("Transfer execution completed, taskId={}", taskId);
            return CompletableFuture.completedFuture(null);
        } catch (TaskPauseRequestedException ex) {
            log.info("Transfer execution paused, taskId={}, message={}", taskId, ex.getMessage());
            executionStateService.flushBufferedFileStatuses(taskId);
            executionStateService.updateTaskStatus(taskId, TransferStatus.PAUSED, ex.getMessage());
            return CompletableFuture.completedFuture(null);
        } catch (TaskCancelRequestedException ex) {
            log.info("Transfer execution canceled, taskId={}, message={}", taskId, ex.getMessage());
            executionStateService.flushBufferedFileStatuses(taskId);
            executionStateService.updateTaskStatus(taskId, TransferStatus.CANCELED, ex.getMessage());
            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            log.error("Transfer execution failed, taskId={}", taskId, ex);
            executionStateService.flushBufferedFileStatuses(taskId);
            try {
                executionStateService.updateTaskStatus(taskId, TransferStatus.FAILED, ex.getMessage());
            } catch (Exception statusEx) {
                ex.addSuppressed(statusEx);
            }
            return CompletableFuture.failedFuture(ex);
        } finally {
            scalableRuntimeMonitorService.markTaskFinished(taskId);
            scalableTaskControlService.clearSignals(taskId);
            lock.unlock();
            log.debug("Execution round finished and coordination lock released, taskId={}", taskId);
        }
    }

    /**
     * 运行单个任务的完整批次调度流程。
     */
    private void runExecution(String taskId) {
        TransferTask task = statePersistenceService.getTask(taskId);
        validateTaskState(task);

        Path sourceRoot = Paths.get(task.getSourcePath()).toAbsolutePath().normalize();
        Path targetRoot = Paths.get(task.getTargetPath()).toAbsolutePath().normalize();
        log.debug("Preparing transfer execution, taskId={}, sourceRoot={}, targetRoot={}, totalBatches={}, totalFiles={}",
                taskId, sourceRoot, targetRoot, task.getTotalBatches(), task.getTotalFiles());

        executionStateService.updateTaskStatus(taskId, TransferStatus.RUNNING, null);

        if (task.getTotalFiles() == 0L || task.getTotalBatches() == 0L) {
            log.info("Task has no executable files and will complete immediately, taskId={}", taskId);
            scalableProgressService.complete(taskId);
            executionStateService.updateTaskStatus(taskId, TransferStatus.COMPLETED, null);
            return;
        }

        AtomicBoolean aborted = new AtomicBoolean(false);
        int batchPageSize = transferProperties.getScalableExecutionBatchPageSize();
        int prefetchSize = Math.max(batchPageSize, batchPageSize * transferProperties.getSchedulingPrefetchMultiplier());
        List<BatchStatus> activeBatchStatuses = List.of(
                BatchStatus.SCANNED,
                BatchStatus.PAUSED,
                BatchStatus.FAILED,
                BatchStatus.RUNNING
        );
        rebuildDispatchQueue(taskId, activeBatchStatuses, prefetchSize);

        while (!aborted.get()) {
            checkpointControl(taskId);
            List<Long> batchIds = dispatchQueueStore.pollBatchIds(
                    taskId,
                    prefetchSize,
                    transferProperties.getHotDispatchBurst(),
                    transferProperties.getWarmDispatchBurst(),
                    transferProperties.getColdDispatchBurst()
            );
            if (batchIds.isEmpty()) {
                log.debug("Dispatch queue is empty, taskId={}", taskId);
                break;
            }

            List<TransferBatch> prioritizedBatches = loadBatchesByIds(batchIds);
            List<CompletableFuture<Void>> batchFutures = new ArrayList<>(prioritizedBatches.size());
            for (TransferBatch batch : prioritizedBatches) {
                waitForDispatchWindow(taskId, batch);
                log.debug("Submitting batch to transfer executor, taskId={}, batchId={}, batchNumber={}, status={}",
                        taskId, batch.getId(), batch.getBatchNumber(), batch.getStatus());
                batchFutures.add(CompletableFuture.runAsync(() -> processBatch(
                        taskId,
                        task,
                        sourceRoot,
                        targetRoot,
                        batch,
                        aborted
                ), transferExecutor));
            }

            waitForBatches(taskId, batchFutures, aborted);
        }

        if (aborted.get()) {
            throw new TransferException("Transfer execution aborted");
        }

        scalableProgressService.complete(taskId);
        executionStateService.flushBufferedFileStatuses(taskId);
        executionStateService.updateTaskStatus(taskId, TransferStatus.COMPLETED, null);
    }

    /**
     * 处理单个批次并更新批次级状态流转。
     */
    private void processBatch(String taskId,
                              TransferTask task,
                              Path sourceRoot,
                              Path targetRoot,
                              TransferBatch batch,
                              AtomicBoolean aborted) {
        if (aborted.get()) {
            log.debug("Skipping batch because task execution is already aborted, taskId={}, batchId={}", taskId, batch.getId());
            return;
        }

        AtomicLong batchTransferredBytes = new AtomicLong(Math.max(0L, batch.getTransferredBytes()));
        AtomicLong lastPersistedBatchBytes = new AtomicLong(batchTransferredBytes.get());
        try {
            log.info("Starting batch execution, taskId={}, batchId={}, batchNumber={}, fileCount={}",
                    taskId, batch.getId(), batch.getBatchNumber(), batch.getFileCount());
            scalableRuntimeMonitorService.onBatchStarted(taskId, batch.getTemperatureTier(), batch.getTotalBytes());
            checkpointControl(taskId);
            executionStateService.updateBatchStatus(batch.getId(), BatchStatus.RUNNING, batchTransferredBytes.get(), null);
            processFileRecords(taskId, task, sourceRoot, targetRoot, batch, batchTransferredBytes, lastPersistedBatchBytes, aborted);
            checkpointControl(taskId);
            executionStateService.flushBufferedFileStatuses(taskId);
            executionStateService.updateBatchStatus(batch.getId(), BatchStatus.COMPLETED, batchTransferredBytes.get(), null);
            log.info("Batch execution completed, taskId={}, batchId={}, batchNumber={}, transferredBytes={}",
                    taskId, batch.getId(), batch.getBatchNumber(), batchTransferredBytes.get());
        } catch (TaskPauseRequestedException ex) {
            executionStateService.flushBufferedFileStatuses(taskId);
            log.info("Batch execution paused, taskId={}, batchId={}, batchNumber={}, transferredBytes={}",
                    taskId, batch.getId(), batch.getBatchNumber(), batchTransferredBytes.get());
            executionStateService.updateBatchStatus(batch.getId(), BatchStatus.PAUSED, batchTransferredBytes.get(), ex.getMessage());
            throw ex;
        } catch (TaskCancelRequestedException ex) {
            executionStateService.flushBufferedFileStatuses(taskId);
            log.info("Batch execution canceled, taskId={}, batchId={}, batchNumber={}, transferredBytes={}",
                    taskId, batch.getId(), batch.getBatchNumber(), batchTransferredBytes.get());
            aborted.set(true);
            executionStateService.updateBatchStatus(batch.getId(), BatchStatus.CANCELED, batchTransferredBytes.get(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            executionStateService.flushBufferedFileStatuses(taskId);
            log.error("Batch execution failed, taskId={}, batchId={}, batchNumber={}",
                    taskId, batch.getId(), batch.getBatchNumber(), ex);
            aborted.set(true);
            executionStateService.updateBatchStatus(batch.getId(), BatchStatus.FAILED, batchTransferredBytes.get(), ex.getMessage());
            throw new TransferException("Batch execution failed: " + batch.getBatchNumber(), ex);
        } finally {
            executionStateService.flushBufferedFileStatuses(taskId);
            scalableRuntimeMonitorService.onBatchFinished(taskId, batch.getTemperatureTier(), batch.getTotalBytes());
        }
    }

    /**
     * 分页处理批次中所有可执行的文件记录。
     */
    private void processFileRecords(String taskId,
                                    TransferTask task,
                                    Path sourceRoot,
                                    Path targetRoot,
                                    TransferBatch batch,
                                    AtomicLong batchTransferredBytes,
                                    AtomicLong lastPersistedBatchBytes,
                                    AtomicBoolean aborted) {
        int filePageSize = transferProperties.getScalableExecutionFilePageSize();
        List<FileTransferStatus> activeFileStatuses = List.of(
                FileTransferStatus.PENDING,
                FileTransferStatus.PAUSED,
                FileTransferStatus.FAILED,
                FileTransferStatus.RUNNING
        );
        long fileCursor = 0L;

        while (!aborted.get()) {
            checkpointControl(taskId);

            List<ScalableFileRecord> fileRecords = scalableFileRecordMapper.selectList(
                    new LambdaQueryWrapper<ScalableFileRecord>()
                            .eq(ScalableFileRecord::getTaskId, taskId)
                            .eq(ScalableFileRecord::getBatchId, batch.getId())
                            .in(ScalableFileRecord::getStatus, activeFileStatuses)
                            .gt(ScalableFileRecord::getId, fileCursor)
                            .orderByAsc(ScalableFileRecord::getId)
                            .last("limit " + filePageSize)
            );

            if (fileRecords.isEmpty()) {
                log.debug("No more runnable file records for batch, taskId={}, batchId={}, fileCursor={}",
                        taskId, batch.getId(), fileCursor);
                break;
            }

            log.debug("Loaded file slice, taskId={}, batchId={}, fileCursor={}, sliceSize={}",
                    taskId, batch.getId(), fileCursor, fileRecords.size());

            for (ScalableFileRecord record : fileRecords) {
                if (aborted.get()) {
                    return;
                }
                checkpointControl(taskId);
                fileCursor = record.getId();
                processSingleRecord(taskId, task, sourceRoot, targetRoot, batch, record, batchTransferredBytes, lastPersistedBatchBytes);
            }
            executionStateService.flushBufferedFileStatuses(taskId);
        }
    }

    /**
     * 复制单条文件记录并更新文件级状态。
     */
    private void processSingleRecord(String taskId,
                                     TransferTask task,
                                     Path sourceRoot,
                                     Path targetRoot,
                                     TransferBatch batch,
                                     ScalableFileRecord record,
                                     AtomicLong batchTransferredBytes,
                                     AtomicLong lastPersistedBatchBytes) {
        AtomicLong currentTransferred = new AtomicLong(Math.max(0L, record.getTransferredBytes()));
        executionStateService.updateFileStatus(record.getId(), taskId, FileTransferStatus.RUNNING, currentTransferred.get(), null, null);
        scalableRuntimeMonitorService.onFileStarted(taskId);
        log.debug("Starting file processing, taskId={}, batchId={}, recordId={}, relativePath={}, persistedOffset={}",
                taskId, batch.getId(), record.getId(), record.getRelativePath(), currentTransferred.get());

        try {
            ScalableFileCopyService.CopyResult result = copyFileWithRetry(
                    taskId,
                    task,
                    sourceRoot,
                    targetRoot,
                    batch,
                    record,
                    currentTransferred,
                    batchTransferredBytes,
                    lastPersistedBatchBytes
            );

            long completedBytes = Math.max(currentTransferred.get(), result.transferredBytes());
            currentTransferred.set(completedBytes);
            executionStateService.updateFileStatus(
                    record.getId(),
                    taskId,
                    FileTransferStatus.COMPLETED,
                    completedBytes,
                    null,
                    result.targetHash()
            );
            scalableProgressService.increment(taskId, 0, 1);
            log.debug("File processing completed, taskId={}, batchId={}, recordId={}, transferredBytes={}, targetHash={}",
                    taskId, batch.getId(), record.getId(), completedBytes, result.targetHash());
        } catch (TaskPauseRequestedException ex) {
            executionStateService.updateFileStatus(
                    record.getId(),
                    taskId,
                    FileTransferStatus.PAUSED,
                    currentTransferred.get(),
                    ex.getMessage(),
                    null
            );
            executionStateService.updateBatchStatus(batch.getId(), BatchStatus.PAUSED, batchTransferredBytes.get(), ex.getMessage());
            throw ex;
        } catch (TaskCancelRequestedException ex) {
            executionStateService.updateFileStatus(
                    record.getId(),
                    taskId,
                    FileTransferStatus.CANCELED,
                    currentTransferred.get(),
                    ex.getMessage(),
                    null
            );
            executionStateService.updateBatchStatus(batch.getId(), BatchStatus.CANCELED, batchTransferredBytes.get(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            executionStateService.updateFileStatus(
                    record.getId(),
                    taskId,
                    FileTransferStatus.FAILED,
                    currentTransferred.get(),
                    ex.getMessage(),
                    null
            );
            executionStateService.updateBatchStatus(batch.getId(), BatchStatus.FAILED, batchTransferredBytes.get(), ex.getMessage());
            throw new TransferException("File execution failed: " + record.getRelativePath(), ex);
        } finally {
            scalableRuntimeMonitorService.onFileFinished(taskId);
        }
    }

    /**
     * 以指数退避方式重试复制单个文件。
     */
    private ScalableFileCopyService.CopyResult copyFileWithRetry(String taskId,
                                                                 TransferTask task,
                                                                 Path sourceRoot,
                                                                 Path targetRoot,
                                                                 TransferBatch batch,
                                                                 ScalableFileRecord record,
                                                                 AtomicLong currentTransferred,
                                                                 AtomicLong batchTransferredBytes,
                                                                 AtomicLong lastPersistedBatchBytes) {
        int maxAttempts = transferProperties.getMaxRetries() + 1;
        long backoffMillis = transferProperties.getInitialRetryIntervalMillis();
        Exception lastException = null;
        AtomicLong lastLoggedTransferred = new AtomicLong(currentTransferred.get());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            checkpointControl(taskId);
            try {
                if (attempt > 1) {
                    scalableRuntimeMonitorService.onRetry(taskId);
                    log.debug("Retrying file copy, taskId={}, batchId={}, recordId={}, attempt={}, maxAttempts={}, offset={}",
                            taskId, batch.getId(), record.getId(), attempt, maxAttempts, currentTransferred.get());
                }
                return scalableFileCopyService.copyFile(
                        sourceRoot,
                        targetRoot,
                        task.getVerificationMode(),
                        record,
                        (bytesDelta, absoluteTransferred) -> {
                            currentTransferred.set(absoluteTransferred);
                            long currentBatchBytes = batchTransferredBytes.addAndGet(bytesDelta);
                            scalableProgressService.increment(taskId, bytesDelta, 0);
                            persistBatchProgressIfNeeded(batch, currentBatchBytes, lastPersistedBatchBytes);
                            if (shouldLogFileProgress(record, absoluteTransferred, lastLoggedTransferred)) {
                                log.debug("File copy progress advanced, taskId={}, batchId={}, recordId={}, bytesDelta={}, absoluteTransferred={}, batchTransferredBytes={}",
                                        taskId, batch.getId(), record.getId(), bytesDelta, absoluteTransferred, currentBatchBytes);
                            }
                            checkpointControl(taskId);
                        }
                );
            } catch (TaskPauseRequestedException | TaskCancelRequestedException ex) {
                throw ex;
            } catch (Exception ex) {
                lastException = ex;
                log.debug("File copy failed and retry policy will be evaluated, taskId={}, batchId={}, recordId={}, attempt={}, message={}",
                        taskId, batch.getId(), record.getId(), attempt, ex.getMessage());
                if (attempt == maxAttempts) {
                    break;
                }
                record.setTransferredBytes(currentTransferred.get());
                sleepBackoff(backoffMillis);
                backoffMillis = Math.min(backoffMillis * 2, 30_000L);
            }
        }

        throw new TransferException("File copy exhausted retries: " + record.getRelativePath(), lastException);
    }

    /**
     * 限制大文件的调试进度日志频率。
     */
    private boolean shouldLogFileProgress(ScalableFileRecord record,
                                          long absoluteTransferred,
                                          AtomicLong lastLoggedTransferred) {
        long interval = Math.max(16L * 1024 * 1024, transferProperties.getBufferSize() * 32L);
        long previousLogged = lastLoggedTransferred.get();
        boolean reachedInterval = absoluteTransferred - previousLogged >= interval;
        boolean finished = absoluteTransferred >= record.getSourceSize();
        return (reachedInterval || finished)
                && lastLoggedTransferred.compareAndSet(previousLogged, absoluteTransferred);
    }

    /**
     * 根据已持久化的批次状态重建内存或 Redis 驱动的派发队列。
     */
    private void rebuildDispatchQueue(String taskId, List<BatchStatus> statuses, int pageSize) {
        dispatchQueueStore.clearTask(taskId);
        int batchCursor = 0;
        while (true) {
            List<TransferBatch> batchRecords = transferBatchMapper.selectNextBatches(taskId, statuses, batchCursor, pageSize);
            if (batchRecords.isEmpty()) {
                break;
            }
            for (TransferBatch batch : batchRecords) {
                dispatchQueueStore.enqueueBatch(taskId, batch);
                batchCursor = batch.getBatchNumber();
            }
        }
        log.info("Rebuilt dispatch queue from persisted state, taskId={}, lastBatchCursor={}", taskId, batchCursor);
    }

    /**
     * 将查询结果恢复为原始队列顺序。
     */
    private List<TransferBatch> loadBatchesByIds(List<Long> batchIds) {
        Map<Long, TransferBatch> batchMap = new HashMap<>();
        for (TransferBatch batch : transferBatchMapper.selectByIds(batchIds)) {
            batchMap.put(batch.getId(), batch);
        }
        return batchIds.stream()
                .map(batchMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private void waitForDispatchWindow(String taskId, TransferBatch batch) {
        while (true) {
            checkpointControl(taskId);
            var snapshot = scalableRuntimeMonitorService.snapshot(taskId);
            var decision = scalableSchedulingService.evaluateDispatch(taskId, batch, transferExecutor, snapshot);
            if (!decision.throttled()) {
                scalableRuntimeMonitorService.clearThrottle(taskId);
                return;
            }
            scalableRuntimeMonitorService.onThrottle(taskId, decision.reasons());
            sleepBackoff(transferProperties.getDispatchThrottleSleepMillis());
        }
    }

    /**
     * 按周期持久化批次进度，而不是在每个文件完成时都写库。
     */
    private void persistBatchProgressIfNeeded(TransferBatch batch,
                                              long currentBatchBytes,
                                              AtomicLong lastPersistedBatchBytes) {
        long persisted = lastPersistedBatchBytes.get();
        long interval = transferProperties.getProgressSaveIntervalBytes();
        if (currentBatchBytes - persisted < interval) {
            return;
        }
        if (lastPersistedBatchBytes.compareAndSet(persisted, currentBatchBytes)) {
            executionStateService.updateBatchStatus(batch.getId(), BatchStatus.RUNNING, currentBatchBytes, null);
            log.debug("Persisted batch progress checkpoint, batchId={}, batchNumber={}, persistedBytes={}",
                    batch.getId(), batch.getBatchNumber(), currentBatchBytes);
        }
    }

    /**
     * 在安全检查点检查暂停或取消信号。
     */
    private void checkpointControl(String taskId) {
        if (scalableTaskControlService.isCancelRequested(taskId)) {
            throw new TaskCancelRequestedException("Cancel requested");
        }
        if (scalableTaskControlService.isPauseRequested(taskId)) {
            throw new TaskPauseRequestedException("Pause requested");
        }
    }

    /**
     * 按退避或限流时长进入休眠。
     */
    private void sleepBackoff(long backoffMillis) {
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TransferException("Retry backoff interrupted", ex);
        }
    }

    /**
     * 等待当前派发轮次结束，并重新抛出首个失败异常。
     */
    private void waitForBatches(String taskId, List<CompletableFuture<Void>> batchFutures, AtomicBoolean aborted) {
        List<Throwable> failures = new ArrayList<>(1);
        for (CompletableFuture<Void> future : batchFutures) {
            try {
                future.join();
            } catch (Exception ex) {
                aborted.set(true);
                failures.add(ex);
            }
        }
        if (!failures.isEmpty()) {
            Throwable first = failures.getFirst();
            if (first instanceof CompletionException completionException && completionException.getCause() != null) {
                first = completionException.getCause();
            }
            log.debug("Detected batch future failures while waiting, taskId={}, failureCount={}", taskId, failures.size());
            if (first instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new TransferException("Batch execution failed", first);
        }
    }

    /**
     * 校验任务是否处于可执行状态。
     */
    private void validateTaskState(TransferTask task) {
        if (task.getStatus() == TransferStatus.COMPLETED || task.getStatus() == TransferStatus.CANCELED) {
            throw new TransferException("Task is not executable in current status: " + task.getStatus());
        }
        if (task.getStatus() != TransferStatus.PLANNED
                && task.getStatus() != TransferStatus.FAILED
                && task.getStatus() != TransferStatus.PAUSED
                && task.getStatus() != TransferStatus.RUNNING) {
            throw new TransferException("Task is not ready for execution: " + task.getStatus());
        }
    }
}
