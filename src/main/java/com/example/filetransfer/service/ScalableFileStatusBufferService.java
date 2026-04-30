package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.domain.FileTransferStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 文件状态缓冲刷库服务。
 * 负责把频繁的单文件状态更新汇聚成批量 JDBC 更新，并保证刷库失败时不丢数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScalableFileStatusBufferService {

    /** 全局迁移配置。 */
    private final TransferProperties transferProperties;
    /** 文件状态 JDBC 批量更新服务。 */
    private final ScalableFileRecordJdbcService scalableFileRecordJdbcService;

    /** 每个任务待刷新的文件状态队列。 */
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<FileStatusUpdateCommand>> pendingUpdates =
            new ConcurrentHashMap<>();
    /** 每个任务当前积压的待刷数量。 */
    private final ConcurrentHashMap<String, AtomicInteger> pendingCounts = new ConcurrentHashMap<>();
    /** 每个任务单独的刷库锁，避免同一任务并发刷库。 */
    private final ConcurrentHashMap<String, ReentrantLock> flushLocks = new ConcurrentHashMap<>();

    /**
     * 缓冲一条文件状态更新，当达到批量阈值时触发异步刷库。
     */
    public void enqueue(String taskId,
                        Long recordId,
                        FileTransferStatus status,
                        long transferredBytes,
                        String lastError,
                        String targetHash) {
        ConcurrentLinkedQueue<FileStatusUpdateCommand> queue =
                pendingUpdates.computeIfAbsent(taskId, ignored -> new ConcurrentLinkedQueue<>());
        AtomicInteger pendingCount = pendingCounts.computeIfAbsent(taskId, ignored -> new AtomicInteger());
        // 先把更新命令入队，等待后续批量刷新。
        queue.add(new FileStatusUpdateCommand(taskId, recordId, status, transferredBytes, lastError, targetHash));
        // 只有在数量达到阈值时才主动触发异步刷库，减少数据库写放大。
        if (pendingCount.incrementAndGet() >= transferProperties.getStateFlushBatchSize()) {
            flushTaskAsync(taskId);
        }
    }

    @Async("stateFlushExecutor")
    public CompletableFuture<Void> flushTaskAsync(String taskId) {
        // 异步线程只负责调 flushTask，本身不持有额外业务逻辑。
        flushTask(taskId);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 周期性扫描所有任务的本地状态缓冲区并尝试刷库。
     */
    @Scheduled(fixedDelayString = "${transfer.state-flush-interval-millis:500}")
    public void scheduledFlush() {
        for (String taskId : pendingUpdates.keySet()) {
            flushTask(taskId);
        }
    }

    public void flushTask(String taskId) {
        ConcurrentLinkedQueue<FileStatusUpdateCommand> queue = pendingUpdates.get(taskId);
        // 没有待刷数据时直接返回，避免空转。
        if (queue == null || queue.isEmpty()) {
            return;
        }
        ReentrantLock lock = flushLocks.computeIfAbsent(taskId, ignored -> new ReentrantLock());
        // 同一任务如果已经有线程在刷库，则当前线程直接跳过，避免重复消费队列。
        if (!lock.tryLock()) {
            return;
        }

        try {
            int batchSize = transferProperties.getStateFlushBatchSize();
            List<FileStatusUpdateCommand> batch = new ArrayList<>(batchSize);
            try {
                FileStatusUpdateCommand command;
                while ((command = queue.poll()) != null) {
                    // 每 poll 出来一条就从待刷计数里减掉，保持计数大致同步。
                    pendingCounts.get(taskId).decrementAndGet();
                    batch.add(command);
                    if (batch.size() >= batchSize) {
                        // 达到批次大小就立即刷一次，降低单批内存占用。
                        flushBatch(batch, batchSize);
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    // 循环结束后，别忘了把最后不足一批的数据也刷掉。
                    flushBatch(batch, batchSize);
                }
            } catch (RuntimeException ex) {
                // 如果刷库失败，要把本次已取出的数据重新放回队列，避免状态永久丢失。
                requeueBatch(taskId, batch);
                log.warn("Failed to flush buffered file statuses and re-queued the batch, taskId={}, requeuedSize={}",
                        taskId, batch.size(), ex);
                throw ex;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 通过 JDBC 批量写入一批文件状态更新。
     */
    private void flushBatch(List<FileStatusUpdateCommand> batch, int batchSize) {
        scalableFileRecordJdbcService.batchUpdateStatuses(batch, batchSize);
        if (log.isDebugEnabled()) {
            log.debug("Flushed file status updates, batchSize={}, sampleTaskId={}",
                    batch.size(), batch.getFirst().taskId());
        }
    }

    /**
     * 失败时把已出队但尚未成功写库的一批命令重新入队。
     */
    private void requeueBatch(String taskId, List<FileStatusUpdateCommand> batch) {
        if (batch.isEmpty()) {
            return;
        }
        ConcurrentLinkedQueue<FileStatusUpdateCommand> queue =
                pendingUpdates.computeIfAbsent(taskId, ignored -> new ConcurrentLinkedQueue<>());
        AtomicInteger pendingCount = pendingCounts.computeIfAbsent(taskId, ignored -> new AtomicInteger());
        for (FileStatusUpdateCommand command : batch) {
            // 重新按原命令内容回队，等待下一轮刷库重试。
            queue.add(command);
        }
        // 回队后补回待刷计数。
        pendingCount.addAndGet(batch.size());
    }
}
