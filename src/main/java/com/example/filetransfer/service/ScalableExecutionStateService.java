package com.example.filetransfer.service;

import com.example.filetransfer.domain.BatchStatus;
import com.example.filetransfer.domain.FileTransferStatus;
import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.repository.TransferBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 大规模执行阶段状态更新服务。
 * 集中处理任务、批次和文件明细的状态推进。
 */
@Service
@RequiredArgsConstructor
public class ScalableExecutionStateService {

    /** 任务和聚合进度持久化服务。 */
    private final StatePersistenceService statePersistenceService;

    /** 批次仓库。 */
    private final TransferBatchRepository transferBatchRepository;

    /** 文件状态缓冲刷库服务。 */
    private final ScalableFileStatusBufferService scalableFileStatusBufferService;

    /**
     * 更新任务状态。
     *
     * @param taskId 任务 ID
     * @param status 目标状态
     * @param lastError 最近一次错误信息
     */
    @Transactional
    public void updateTaskStatus(String taskId, TransferStatus status, String lastError) {
        statePersistenceService.updateTaskStatus(taskId, status, lastError);
    }

    /**
     * 更新批次状态及已传输字节数。
     *
     * @param batchId 批次 ID
     * @param status 目标状态
     * @param transferredBytes 已传输字节数
     * @param lastError 最近一次错误信息
     */
    @Transactional
    public void updateBatchStatus(Long batchId, BatchStatus status, long transferredBytes, String lastError) {
        int updated = transferBatchRepository.updateBatchProgressAndStatus(batchId, status, transferredBytes, lastError);
        if (updated == 0) {
            throw new IllegalStateException("Batch not found: " + batchId);
        }
    }

    /**
     * 缓冲文件状态更新。
     *
     * @param recordId 文件记录 ID
     * @param taskId 任务 ID
     * @param status 文件状态
     * @param transferredBytes 已传输字节数
     * @param lastError 最近一次错误信息
     * @param targetHash 目标文件哈希
     */
    public void updateFileStatus(Long recordId,
                                 String taskId,
                                 FileTransferStatus status,
                                 long transferredBytes,
                                 String lastError,
                                 String targetHash) {
        // 文件状态更新频率非常高，因此先进入缓冲区，再由后台批量刷库。
        scalableFileStatusBufferService.enqueue(taskId, recordId, status, transferredBytes, lastError, targetHash);
    }

    /**
     * 立刻刷出指定任务缓冲中的文件状态更新。
     *
     * @param taskId 任务 ID
     */
    public void flushBufferedFileStatuses(String taskId) {
        scalableFileStatusBufferService.flushTask(taskId);
    }
}
