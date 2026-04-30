package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.FileTransferStatus;
import com.example.filetransfer.domain.ScalableFileRecord;
import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.TransferTask;
import com.example.filetransfer.domain.TransferType;
import com.example.filetransfer.domain.VerificationMode;
import com.example.filetransfer.dto.ScalableTransferPlanResponse;
import com.example.filetransfer.exception.TransferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 大规模迁移计划服务。
 * 负责扫描源目录、按阈值切分批次，并把规划结果持久化为后续执行所需的数据。
 */
@Slf4j
@Service
public class ScalableTransferPlannerService {

    /** 全局迁移配置。 */
    private final TransferProperties transferProperties;
    /** 任务与进度冷状态持久化服务。 */
    private final StatePersistenceService statePersistenceService;
    /** 批次及文件明细落库服务。 */
    private final ScalableBatchPersistenceService scalableBatchPersistenceService;
    /** 聚合进度服务。 */
    private final ScalableProgressService scalableProgressService;
    /** 调度策略服务，用于计算冷热分层和优先级。 */
    private final ScalableSchedulingService scalableSchedulingService;
    /** 调度热队列存储。 */
    private final DispatchQueueStore dispatchQueueStore;
    /** 规划协调线程池。 */
    private final ThreadPoolTaskExecutor transferCoordinatorExecutor;

    public ScalableTransferPlannerService(TransferProperties transferProperties,
                                          StatePersistenceService statePersistenceService,
                                          ScalableBatchPersistenceService scalableBatchPersistenceService,
                                          ScalableProgressService scalableProgressService,
                                          ScalableSchedulingService scalableSchedulingService,
                                          DispatchQueueStore dispatchQueueStore,
                                          @Qualifier("transferCoordinatorExecutor")
                                          ThreadPoolTaskExecutor transferCoordinatorExecutor) {
        this.transferProperties = transferProperties;
        this.statePersistenceService = statePersistenceService;
        this.scalableBatchPersistenceService = scalableBatchPersistenceService;
        this.scalableProgressService = scalableProgressService;
        this.scalableSchedulingService = scalableSchedulingService;
        this.dispatchQueueStore = dispatchQueueStore;
        this.transferCoordinatorExecutor = transferCoordinatorExecutor;
    }

    /**
     * 创建迁移任务并立即调度后台规划。
     *
     * @param sourcePath 源路径
     * @param targetPath 目标路径
     * @param verificationMode 校验模式
     * @return 创建计划响应
     */
    public ScalableTransferPlanResponse createPlan(String sourcePath, String targetPath, VerificationMode verificationMode) {
        // 先把入参路径标准化，避免后续比较时受相对路径和冗余分隔符影响。
        Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
        Path target = Paths.get(targetPath).toAbsolutePath().normalize();
        // 允许调用方不传校验模式，此时回退到系统默认的大小+修改时间校验。
        VerificationMode resolvedMode = verificationMode == null ? VerificationMode.SIZE_AND_MTIME : verificationMode;

        validateCreatePlanRequest(source, target);

        // 先创建任务主记录，并把状态置为 PLANNING，表示已经接单但尚未规划完成。
        TransferTask task = statePersistenceService.saveTask(TransferTask.builder()
                .sourcePath(source.toString())
                .targetPath(target.toString())
                .transferType(TransferType.DIRECTORY)
                .status(TransferStatus.PLANNING)
                .totalBytes(0L)
                .transferredBytes(0L)
                .totalFiles(0L)
                .totalBatches(0L)
                .hashAlgorithm(transferProperties.getHashAlgorithm())
                .verificationMode(resolvedMode)
                .retryCount(0)
                .build());

        // 规划开始前先初始化进度与热队列，避免残留旧数据干扰新任务。
        scalableProgressService.initialize(task.getId(), 0L, 0L);
        dispatchQueueStore.clearTask(task.getId());
        // 真正的目录扫描和切批放到后台线程执行，HTTP 请求可以立即返回。
        schedulePlanning(task.getId(), source, target, resolvedMode);
        log.info("Accepted asynchronous planning request, taskId={}, source={}, target={}, verificationMode={}",
                task.getId(), source, target, resolvedMode);

        return new ScalableTransferPlanResponse(
                task.getId(),
                TransferStatus.PLANNING,
                0L,
                0L,
                0L,
                transferProperties.getScalableBatchFileCount(),
                transferProperties.getScalableBatchBytes(),
                resolvedMode,
                "Planning started asynchronously. Poll the task summary until status becomes PLANNED."
        );
    }

    private void schedulePlanning(String taskId, Path source, Path target, VerificationMode verificationMode) {
        // 通过协调线程池异步执行规划，避免阻塞请求线程。
        CompletableFuture.runAsync(
                () -> runPlanning(taskId, source, target, verificationMode),
                transferCoordinatorExecutor
        ).whenComplete((ignored, error) -> {
            if (error != null) {
                log.error("Planning future completed exceptionally, taskId={}", taskId, error);
            }
        });
    }

    private void runPlanning(String taskId, Path source, Path target, VerificationMode verificationMode) {
        log.info("Starting scalable transfer planning, taskId={}, source={}, target={}, verificationMode={}",
                taskId, source, target, verificationMode);

        // 该累加器在规划期间持续累计总文件数、总字节数和当前批次内容。
        PlanningAccumulator accumulator = new PlanningAccumulator(
                taskId,
                transferProperties.getScalablePersistChunkSize()
        );
        // 扫描线程与消费线程通过阻塞队列解耦，避免一次性把全量文件装进内存。
        BlockingQueue<ScannedFileDescriptor> pipeline =
                new ArrayBlockingQueue<>(transferProperties.getScanningPipelineQueueCapacity());
        // 扫描线程如果出错，会把异常记录到这里，由消费线程统一抛出。
        AtomicReference<Throwable> scanFailure = new AtomicReference<>();
        CompletableFuture<Void> scanFuture = CompletableFuture.runAsync(
                () -> scanSourceDirectory(source, pipeline, scanFailure)
        );

        try {
            // 消费扫描结果，并按阈值持续切批。
            consumePipeline(accumulator, pipeline, scanFuture, scanFailure);
            // 消费完成后，别忘了把最后一个未满批次也落库。
            flushBatch(accumulator);
            // 规划成功后再把任务状态推进到 PLANNED。
            finalizeTask(taskId, accumulator);
            log.info("Scalable transfer plan created, taskId={}, totalFiles={}, totalBytes={}, batchCount={}, verificationMode={}",
                    taskId, accumulator.totalFiles, accumulator.totalBytes, accumulator.batchCount, verificationMode);
        } catch (Exception ex) {
            markTaskFailed(taskId, accumulator, ex);
            log.error("Failed to create transfer plan, taskId={}, source={}, target={}", taskId, source, target, ex);
        }
    }

    private void validateCreatePlanRequest(Path source, Path target) {
        // 大规模模式只支持以现有目录作为源端。
        if (!Files.exists(source) || !Files.isDirectory(source)) {
            throw new TransferException("Scalable mode requires an existing source directory: " + source);
        }
        // 禁止源和目标互相嵌套，避免迁移过程中把新写入内容再次扫描进去。
        if (target.startsWith(source) || source.startsWith(target)) {
            throw new TransferException("Scalable mode does not allow nested source/target directories");
        }
    }

    private void appendFileRecord(PlanningAccumulator accumulator, ScannedFileDescriptor descriptor) {
        long fileSize = descriptor.fileSize();
        // 如果再塞一个文件就会超出批次阈值，则先把当前批次落库。
        if (shouldFlush(accumulator.currentBatchFiles, accumulator.currentBatchBytes, fileSize)) {
            flushBatch(accumulator);
        }

        // 这里只生成轻量文件记录，真正执行时再按批次分页拉取。
        accumulator.currentBatchRecords.add(ScalableFileRecord.builder()
                .taskId(accumulator.taskId)
                .batchId(-1L)
                .relativePath(descriptor.relativePath())
                .sourceSize(fileSize)
                .transferredBytes(0L)
                .sourceLastModifiedMillis(descriptor.lastModifiedMillis())
                .status(FileTransferStatus.PENDING)
                .build());
        accumulator.currentBatchFiles++;
        accumulator.currentBatchBytes += fileSize;
        accumulator.totalFiles++;
        accumulator.totalBytes += fileSize;

        // 大规模扫描时按固定粒度输出进度日志，避免日志量爆炸。
        if (accumulator.totalFiles % 10_000 == 0) {
            log.info("Planning scan progress updated, taskId={}, scannedFiles={}, scannedBytes={}, currentBatchFiles={}, currentBatchBytes={}",
                    accumulator.taskId,
                    accumulator.totalFiles,
                    accumulator.totalBytes,
                    accumulator.currentBatchFiles,
                    accumulator.currentBatchBytes);
        } else {
            log.debug("Queued scanned file into batch, taskId={}, relativePath={}, fileSize={}, currentBatchFiles={}, currentBatchBytes={}",
                    accumulator.taskId,
                    descriptor.relativePath(),
                    fileSize,
                    accumulator.currentBatchFiles,
                    accumulator.currentBatchBytes);
        }
    }

    private boolean shouldFlush(int currentBatchFiles, long currentBatchBytes, long nextFileBytes) {
        // 只要文件数达到阈值，或者再加一个文件后字节数会超阈值，就应当切批。
        return currentBatchFiles >= transferProperties.getScalableBatchFileCount()
                || currentBatchBytes + nextFileBytes > transferProperties.getScalableBatchBytes();
    }

    private void flushBatch(PlanningAccumulator accumulator) {
        // 当前批次没有内容时直接跳过。
        if (accumulator.currentBatchRecords.isEmpty()) {
            return;
        }

        accumulator.batchCount++;
        // 批次落库前先计算冷热分层和调度优先级，执行阶段会直接复用。
        BatchTemperature temperature = scalableSchedulingService.classifyTemperature(
                accumulator.currentBatchBytes,
                accumulator.currentBatchFiles
        );
        int schedulingPriority = scalableSchedulingService.calculateSchedulingPriority(
                temperature,
                accumulator.currentBatchBytes,
                accumulator.currentBatchFiles,
                Math.toIntExact(accumulator.batchCount)
        );

        log.debug("Persisting batch, taskId={}, batchNumber={}, fileCount={}, totalBytes={}",
                accumulator.taskId, accumulator.batchCount, accumulator.currentBatchFiles, accumulator.currentBatchBytes);

        scalableBatchPersistenceService.persistBatch(
                accumulator.taskId,
                Math.toIntExact(accumulator.batchCount),
                accumulator.currentBatchFiles,
                accumulator.currentBatchBytes,
                accumulator.currentBatchRecords,
                temperature,
                schedulingPriority
        );
        log.info("Persisted planning batch, taskId={}, batchNumber={}, temperature={}, priority={}, fileCount={}, totalBytes={}",
                accumulator.taskId,
                accumulator.batchCount,
                temperature,
                schedulingPriority,
                accumulator.currentBatchFiles,
                accumulator.currentBatchBytes);

        // 当前批次落库后，重新初始化下一个批次的累加容器。
        accumulator.currentBatchRecords = new ArrayList<>(accumulator.batchCapacity);
        accumulator.currentBatchFiles = 0;
        accumulator.currentBatchBytes = 0L;
    }

    private void scanSourceDirectory(Path source,
                                     BlockingQueue<ScannedFileDescriptor> pipeline,
                                     AtomicReference<Throwable> scanFailure) {
        try {
            // 逐个文件遍历源目录树，把文件元数据送入扫描流水线。
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        putPipeline(pipeline, new ScannedFileDescriptor(
                                source.relativize(file).toString(),
                                attrs.size(),
                                attrs.lastModifiedTime().toMillis(),
                                false
                        ));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Throwable ex) {
            // 扫描异常不直接在这里抛出，而是记录给消费端统一处理。
            scanFailure.set(ex);
        } finally {
            // 无论成功失败，都投递终止标记，避免消费线程永久阻塞。
            putPipeline(pipeline, ScannedFileDescriptor.createTerminalMarker());
        }
    }

    private void consumePipeline(PlanningAccumulator accumulator,
                                 BlockingQueue<ScannedFileDescriptor> pipeline,
                                 CompletableFuture<Void> scanFuture,
                                 AtomicReference<Throwable> scanFailure) throws InterruptedException {
        while (true) {
            // 阻塞式获取扫描结果，生产者慢时自然等待。
            ScannedFileDescriptor descriptor = pipeline.take();
            if (descriptor.terminalMarker()) {
                // 终止标记出现后，先等待扫描线程真正结束，再检查是否有扫描异常。
                scanFuture.join();
                if (scanFailure.get() != null) {
                    throw new TransferException("Failed to scan scalable source directory", scanFailure.get());
                }
                return;
            }
            // 普通文件元数据则继续加入当前规划批次。
            appendFileRecord(accumulator, descriptor);
        }
    }

    private void putPipeline(BlockingQueue<ScannedFileDescriptor> pipeline, ScannedFileDescriptor descriptor) {
        boolean interrupted = false;
        while (true) {
            try {
                // 使用阻塞写入保证队列满时不会丢数据。
                pipeline.put(descriptor);
                if (interrupted) {
                    // 如果期间被中断过，恢复线程中断标记，避免吞掉中断语义。
                    Thread.currentThread().interrupt();
                }
                return;
            } catch (InterruptedException ex) {
                interrupted = true;
            }
        }
    }

    private void finalizeTask(String taskId, PlanningAccumulator accumulator) {
        // 规划完成后回写任务汇总信息，并把状态推进到 PLANNED。
        TransferTask persistedTask = statePersistenceService.getTask(taskId);
        persistedTask.setStatus(TransferStatus.PLANNED);
        persistedTask.setTotalBytes(accumulator.totalBytes);
        persistedTask.setTotalFiles(accumulator.totalFiles);
        persistedTask.setTotalBatches(accumulator.batchCount);
        persistedTask.setLastError(null);
        statePersistenceService.saveTask(persistedTask);
        // 聚合进度也要同步更新为最终的总文件数和总字节数，供查询接口使用。
        scalableProgressService.initialize(taskId, accumulator.totalBytes, accumulator.totalFiles);
        log.debug("Persisted planning result, taskId={}, totalFiles={}, totalBytes={}, totalBatches={}",
                taskId, accumulator.totalFiles, accumulator.totalBytes, accumulator.batchCount);
    }

    private void markTaskFailed(String taskId, PlanningAccumulator accumulator, Exception ex) {
        // 规划失败时先清理热队列，避免留下半成品调度数据。
        dispatchQueueStore.clearTask(taskId);
        TransferTask failedTask = statePersistenceService.getTask(taskId);
        failedTask.setStatus(TransferStatus.FAILED);
        failedTask.setTotalBytes(accumulator.totalBytes);
        failedTask.setTotalFiles(accumulator.totalFiles);
        failedTask.setTotalBatches(accumulator.batchCount);
        failedTask.setLastError(ex.getMessage());
        statePersistenceService.saveTask(failedTask);
        scalableProgressService.initialize(taskId, accumulator.totalBytes, accumulator.totalFiles);
    }

    /**
     * 规划过程中的内存累加器。
     * 用于维护任务总量和当前待落库批次内容。
     */
    private static class PlanningAccumulator {
        /** 任务 ID。 */
        private final String taskId;
        /** 当前批次的初始容量，减少 ArrayList 扩容次数。 */
        private final int batchCapacity;
        /** 规划期间累计扫描到的总文件数。 */
        private long totalFiles;
        /** 规划期间累计扫描到的总字节数。 */
        private long totalBytes;
        /** 已生成的批次数。 */
        private long batchCount;
        /** 当前批次中的文件数。 */
        private int currentBatchFiles;
        /** 当前批次中的总字节数。 */
        private long currentBatchBytes;
        /** 当前批次待落库的文件记录列表。 */
        private List<ScalableFileRecord> currentBatchRecords;

        private PlanningAccumulator(String taskId, int batchCapacity) {
            this.taskId = taskId;
            this.batchCapacity = batchCapacity;
            this.currentBatchRecords = new ArrayList<>(batchCapacity);
        }
    }

    /**
     * 扫描线程投递到流水线中的轻量文件描述对象。
     *
     * @param relativePath 相对路径
     * @param fileSize 文件大小
     * @param lastModifiedMillis 最后修改时间毫秒值
     * @param terminalMarker 是否为终止标记
     */
    private record ScannedFileDescriptor(String relativePath,
                                         long fileSize,
                                         long lastModifiedMillis,
                                         boolean terminalMarker) {
        private static ScannedFileDescriptor createTerminalMarker() {
            return new ScannedFileDescriptor("", 0L, 0L, true);
        }
    }
}
