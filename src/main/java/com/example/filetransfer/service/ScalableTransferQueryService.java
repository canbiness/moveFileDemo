package com.example.filetransfer.service;

import com.example.filetransfer.domain.BatchStatus;
import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.FileTransferStatus;
import com.example.filetransfer.domain.ThrottleReason;
import com.example.filetransfer.dto.ExecutorMetricsResponse;
import com.example.filetransfer.dto.ScalableTransferBatchPageResponse;
import com.example.filetransfer.dto.ScalableTransferBatchResponse;
import com.example.filetransfer.dto.ScalableTransferMetricsResponse;
import com.example.filetransfer.dto.ScalableTransferTaskSummaryResponse;
import com.example.filetransfer.mapper.ScalableFileRecordMapper;
import com.example.filetransfer.mapper.TransferBatchMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ScalableTransferQueryService {

    private final StatePersistenceService statePersistenceService;
    private final ScalableBatchRepositoryAdapter batchRepositoryAdapter;
    private final TransferBatchMapper transferBatchMapper;
    private final ScalableFileRecordMapper scalableFileRecordMapper;
    private final ScalableTaskControlService scalableTaskControlService;
    private final ScalableRuntimeMonitorService scalableRuntimeMonitorService;
    private final ThreadPoolTaskExecutor transferExecutor;
    private final ThreadPoolTaskExecutor transferCoordinatorExecutor;

    public ScalableTransferQueryService(StatePersistenceService statePersistenceService,
                                        ScalableBatchRepositoryAdapter batchRepositoryAdapter,
                                        TransferBatchMapper transferBatchMapper,
                                        ScalableFileRecordMapper scalableFileRecordMapper,
                                        ScalableTaskControlService scalableTaskControlService,
                                        ScalableRuntimeMonitorService scalableRuntimeMonitorService,
                                        @Qualifier("transferExecutor") ThreadPoolTaskExecutor transferExecutor,
                                        @Qualifier("transferCoordinatorExecutor") ThreadPoolTaskExecutor transferCoordinatorExecutor) {
        this.statePersistenceService = statePersistenceService;
        this.batchRepositoryAdapter = batchRepositoryAdapter;
        this.transferBatchMapper = transferBatchMapper;
        this.scalableFileRecordMapper = scalableFileRecordMapper;
        this.scalableTaskControlService = scalableTaskControlService;
        this.scalableRuntimeMonitorService = scalableRuntimeMonitorService;
        this.transferExecutor = transferExecutor;
        this.transferCoordinatorExecutor = transferCoordinatorExecutor;
    }

    @Transactional(readOnly = true)
    public ScalableTransferTaskSummaryResponse getSummary(String taskId) {
        log.debug("Querying transfer summary, taskId={}", taskId);
        var task = statePersistenceService.getTask(taskId);
        var progress = statePersistenceService.getProgress(taskId);
        return new ScalableTransferTaskSummaryResponse(
                task.getId(),
                task.getSourcePath(),
                task.getTargetPath(),
                task.getStatus(),
                task.getVerificationMode(),
                task.getTotalFiles(),
                task.getTotalBatches(),
                task.getTotalBytes(),
                progress.getTransferredBytes(),
                progress.getProgressPercent(),
                task.getHashAlgorithm(),
                task.getLastError(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public ScalableTransferMetricsResponse getMetrics(String taskId) {
        log.debug("Querying transfer metrics, taskId={}", taskId);
        var task = statePersistenceService.getTask(taskId);
        var progress = statePersistenceService.getProgress(taskId);
        var runtimeSnapshot = scalableRuntimeMonitorService.snapshot(taskId);

        ScalableTransferMetricsResponse response = new ScalableTransferMetricsResponse(
                task.getId(),
                task.getStatus(),
                task.getTotalBatches(),
                task.getTotalFiles(),
                progress.getCompletedFileCount(),
                progress.getTotalBytes(),
                progress.getTransferredBytes(),
                progress.getProgressPercent(),
                progress.getLastCheckpointAt(),
                scalableTaskControlService.peekPauseRequested(taskId),
                scalableTaskControlService.peekCancelRequested(taskId),
                toBatchStatusMap(transferBatchMapper.aggregateStatusCountsByTaskId(taskId)),
                toBatchTemperatureMap(transferBatchMapper.aggregateTemperatureCountsByTaskId(taskId)),
                toFileStatusMap(scalableFileRecordMapper.aggregateStatusCountsByTaskId(taskId)),
                runtimeSnapshot.schedulingStrategy(),
                runtimeSnapshot.backpressureActive(),
                runtimeSnapshot.inFlightBatches(),
                runtimeSnapshot.inFlightFiles(),
                runtimeSnapshot.inFlightBytes(),
                runtimeSnapshot.retryCount(),
                runtimeSnapshot.throttledDispatchCount(),
                runtimeSnapshot.inFlightTemperatureCounts(),
                toThrottleReasonMap(runtimeSnapshot.throttleReasonCounts()),
                toExecutorMetrics(transferExecutor),
                toExecutorMetrics(transferCoordinatorExecutor)
        );
        log.debug("Transfer metrics query completed, taskId={}, taskStatus={}, progressPercent={}",
                taskId, response.status(), response.progressPercent());
        return response;
    }

    @Transactional(readOnly = true)
    public ScalableTransferBatchPageResponse getBatches(String taskId, int page, int size) {
        log.debug("Querying batch page, taskId={}, page={}, size={}", taskId, page, size);
        var pageResult = batchRepositoryAdapter.findBatches(taskId, page, size);
        List<ScalableTransferBatchResponse> responses = pageResult.getContent().stream()
                .map(batch -> new ScalableTransferBatchResponse(
                        batch.getId(),
                        batch.getBatchNumber(),
                        batch.getStatus(),
                        batch.getTemperatureTier(),
                        batch.getSchedulingPriority(),
                        batch.getFileCount(),
                        batch.getTotalBytes(),
                        batch.getTransferredBytes(),
                        batch.getLastError(),
                        batch.getCreatedAt(),
                        batch.getUpdatedAt()
                ))
                .toList();
        return new ScalableTransferBatchPageResponse(
                taskId,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                responses
        );
    }

    private Map<BatchTemperature, Long> toBatchTemperatureMap(List<Map<String, Object>> rows) {
        Map<BatchTemperature, Long> counts = new EnumMap<>(BatchTemperature.class);
        for (BatchTemperature temperature : BatchTemperature.values()) {
            counts.put(temperature, 0L);
        }
        for (Map<String, Object> row : rows) {
            counts.put(BatchTemperature.valueOf(stringValue(row.get("enum_value"))), longValue(row.get("count_value")));
        }
        return counts;
    }

    private Map<ThrottleReason, Long> toThrottleReasonMap(Map<ThrottleReason, Long> rawCounts) {
        Map<ThrottleReason, Long> counts = new EnumMap<>(ThrottleReason.class);
        for (ThrottleReason reason : ThrottleReason.values()) {
            counts.put(reason, rawCounts.getOrDefault(reason, 0L));
        }
        return counts;
    }

    private Map<BatchStatus, Long> toBatchStatusMap(List<Map<String, Object>> rows) {
        Map<BatchStatus, Long> counts = new EnumMap<>(BatchStatus.class);
        for (BatchStatus status : BatchStatus.values()) {
            counts.put(status, 0L);
        }
        for (Map<String, Object> row : rows) {
            counts.put(BatchStatus.valueOf(stringValue(row.get("enum_value"))), longValue(row.get("count_value")));
        }
        return counts;
    }

    private Map<FileTransferStatus, Long> toFileStatusMap(List<Map<String, Object>> rows) {
        Map<FileTransferStatus, Long> counts = new EnumMap<>(FileTransferStatus.class);
        for (FileTransferStatus status : FileTransferStatus.values()) {
            counts.put(status, 0L);
        }
        for (Map<String, Object> row : rows) {
            counts.put(FileTransferStatus.valueOf(stringValue(row.get("enum_value"))), longValue(row.get("count_value")));
        }
        return counts;
    }

    private ExecutorMetricsResponse toExecutorMetrics(ThreadPoolTaskExecutor executor) {
        var threadPoolExecutor = executor.getThreadPoolExecutor();
        return new ExecutorMetricsResponse(
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getPoolSize(),
                executor.getActiveCount(),
                threadPoolExecutor.getQueue().size(),
                threadPoolExecutor.getQueue().remainingCapacity(),
                threadPoolExecutor.getCompletedTaskCount(),
                threadPoolExecutor.getTaskCount()
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }
}
