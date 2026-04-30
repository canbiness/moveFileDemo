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
import com.example.filetransfer.repository.ScalableFileRecordRepository;
import com.example.filetransfer.repository.TransferBatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 大规模任务查询服务。
 * 负责返回任务摘要、运行态快照和批次分页信息。
 */
@Slf4j
@Service
public class ScalableTransferQueryService {

    /** 任务与聚合进度持久化服务。 */
    private final StatePersistenceService statePersistenceService;
    /** 批次分页查询适配器。 */
    private final ScalableBatchRepositoryAdapter batchRepositoryAdapter;
    /** 批次仓库。 */
    private final TransferBatchRepository transferBatchRepository;
    /** 文件记录仓库。 */
    private final ScalableFileRecordRepository scalableFileRecordRepository;
    /** 任务控制信号服务。 */
    private final ScalableTaskControlService scalableTaskControlService;
    /** 运行态监控服务。 */
    private final ScalableRuntimeMonitorService scalableRuntimeMonitorService;
    /** 文件执行线程池。 */
    private final ThreadPoolTaskExecutor transferExecutor;
    /** 协调线程池。 */
    private final ThreadPoolTaskExecutor transferCoordinatorExecutor;

    public ScalableTransferQueryService(StatePersistenceService statePersistenceService,
                                        ScalableBatchRepositoryAdapter batchRepositoryAdapter,
                                        TransferBatchRepository transferBatchRepository,
                                        ScalableFileRecordRepository scalableFileRecordRepository,
                                        ScalableTaskControlService scalableTaskControlService,
                                        ScalableRuntimeMonitorService scalableRuntimeMonitorService,
                                        @Qualifier("transferExecutor") ThreadPoolTaskExecutor transferExecutor,
                                        @Qualifier("transferCoordinatorExecutor") ThreadPoolTaskExecutor transferCoordinatorExecutor) {
        this.statePersistenceService = statePersistenceService;
        this.batchRepositoryAdapter = batchRepositoryAdapter;
        this.transferBatchRepository = transferBatchRepository;
        this.scalableFileRecordRepository = scalableFileRecordRepository;
        this.scalableTaskControlService = scalableTaskControlService;
        this.scalableRuntimeMonitorService = scalableRuntimeMonitorService;
        this.transferExecutor = transferExecutor;
        this.transferCoordinatorExecutor = transferCoordinatorExecutor;
    }

    /**
     * 查询任务摘要信息。
     *
     * @param taskId 任务 ID
     * @return 任务摘要响应
     */
    @Transactional(readOnly = true)
    public ScalableTransferTaskSummaryResponse getSummary(String taskId) {
        log.debug("Querying transfer summary, taskId={}", taskId);
        var task = statePersistenceService.getTask(taskId);
        var progress = statePersistenceService.getProgress(taskId);
        // 摘要接口重点返回当前任务最关心的总量、进度、状态和最后错误信息。
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

    /**
     * 查询任务运行态指标。
     *
     * @param taskId 任务 ID
     * @return 运行态指标响应
     */
    @Transactional(readOnly = true)
    public ScalableTransferMetricsResponse getMetrics(String taskId) {
        log.debug("Querying transfer metrics, taskId={}", taskId);
        var task = statePersistenceService.getTask(taskId);
        var progress = statePersistenceService.getProgress(taskId);
        // 数据库聚合指标和内存运行态快照要组合起来，才能得到完整监控视图。
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
                toBatchStatusMap(transferBatchRepository.aggregateStatusCountsByTaskId(taskId)),
                toBatchTemperatureMap(transferBatchRepository.aggregateTemperatureCountsByTaskId(taskId)),
                toFileStatusMap(scalableFileRecordRepository.aggregateStatusCountsByTaskId(taskId)),
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

    /**
     * 分页查询任务批次列表。
     *
     * @param taskId 任务 ID
     * @param page 页码
     * @param size 页大小
     * @return 批次分页响应
     */
    @Transactional(readOnly = true)
    public ScalableTransferBatchPageResponse getBatches(String taskId, int page, int size) {
        log.debug("Querying batch page, taskId={}, page={}, size={}", taskId, page, size);
        var pageResult = batchRepositoryAdapter.findBatches(taskId, page, size);
        // 只把当前页需要的数据映射成 DTO，避免把实体直接暴露到接口层。
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

    /**
     * 把批次冷热分层聚合结果转换成完整枚举映射。
     */
    private Map<BatchTemperature, Long> toBatchTemperatureMap(List<Object[]> rows) {
        Map<BatchTemperature, Long> counts = new EnumMap<>(BatchTemperature.class);
        // 先把所有枚举补成 0，避免某一类未出现时接口返回缺字段。
        for (BatchTemperature temperature : BatchTemperature.values()) {
            counts.put(temperature, 0L);
        }
        for (Object[] row : rows) {
            counts.put((BatchTemperature) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * 把背压原因快照结果转换为完整枚举映射。
     */
    private Map<ThrottleReason, Long> toThrottleReasonMap(Map<ThrottleReason, Long> rawCounts) {
        Map<ThrottleReason, Long> counts = new EnumMap<>(ThrottleReason.class);
        for (ThrottleReason reason : ThrottleReason.values()) {
            counts.put(reason, rawCounts.getOrDefault(reason, 0L));
        }
        return counts;
    }

    /**
     * 把批次状态聚合结果转换为完整枚举映射。
     */
    private Map<BatchStatus, Long> toBatchStatusMap(List<Object[]> rows) {
        Map<BatchStatus, Long> counts = new EnumMap<>(BatchStatus.class);
        for (BatchStatus status : BatchStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : rows) {
            counts.put((BatchStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * 把文件状态聚合结果转换为完整枚举映射。
     */
    private Map<FileTransferStatus, Long> toFileStatusMap(List<Object[]> rows) {
        Map<FileTransferStatus, Long> counts = new EnumMap<>(FileTransferStatus.class);
        for (FileTransferStatus status : FileTransferStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : rows) {
            counts.put((FileTransferStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * 构造线程池运行快照。
     */
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
}
