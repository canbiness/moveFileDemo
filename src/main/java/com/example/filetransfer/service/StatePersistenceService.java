package com.example.filetransfer.service;

import com.example.filetransfer.domain.TransferProgress;
import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.TransferTask;
import com.example.filetransfer.exception.TransferException;
import com.example.filetransfer.repository.TransferProgressRepository;
import com.example.filetransfer.repository.TransferTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 状态持久化服务。
 * 统一封装任务主记录和聚合进度快照的数据库访问。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatePersistenceService {

    /** 任务主表仓库。 */
    private final TransferTaskRepository transferTaskRepository;

    /** 聚合进度表仓库。 */
    private final TransferProgressRepository transferProgressRepository;

    /**
     * 保存或更新任务主记录。
     *
     * @param task 任务实体
     * @return 持久化后的任务
     */
    @Transactional
    public TransferTask saveTask(TransferTask task) {
        log.debug("Saving transfer task, taskId={}, status={}", task.getId(), task.getStatus());
        return transferTaskRepository.save(task);
    }

    /**
     * 直接更新任务状态和错误信息。
     *
     * @param taskId 任务 ID
     * @param status 目标状态
     * @param lastError 最近一次错误信息
     */
    @Transactional
    public void updateTaskStatus(String taskId, TransferStatus status, String lastError) {
        log.debug("Updating transfer task status, taskId={}, status={}, lastError={}", taskId, status, lastError);
        int updated = transferTaskRepository.updateStatusAndError(taskId, status, lastError);
        if (updated == 0) {
            throw new TransferException("Transfer task not found: " + taskId);
        }
    }

    /**
     * 根据任务 ID 查询任务。
     *
     * @param taskId 任务 ID
     * @return 任务实体
     */
    @Transactional(readOnly = true)
    public TransferTask getTask(String taskId) {
        log.debug("Loading transfer task, taskId={}", taskId);
        return transferTaskRepository.findById(taskId)
                .orElseThrow(() -> new TransferException("Transfer task not found: " + taskId));
    }

    /**
     * 保存任务聚合进度。
     *
     * @param taskId 任务 ID
     * @param totalBytes 总字节数
     * @param transferredBytes 已传输字节数
     * @param fileCount 总文件数
     * @param completedFileCount 已完成文件数
     */
    @Transactional
    public void saveProgress(String taskId, long totalBytes, long transferredBytes, int fileCount, int completedFileCount) {
        // 总字节数为 0 时，说明这是空任务，进度直接视为 100%。
        double percent = totalBytes == 0 ? 100.0D : (transferredBytes * 100.0D) / totalBytes;
        TransferProgress progress = transferProgressRepository.findByTaskId(taskId)
                .orElse(TransferProgress.builder()
                        .taskId(taskId)
                        .build());
        // 每次都按最新聚合值覆盖，避免调用方自己做复杂的增量合并。
        progress.setTotalBytes(totalBytes);
        progress.setTransferredBytes(transferredBytes);
        progress.setFileCount(fileCount);
        progress.setCompletedFileCount(completedFileCount);
        progress.setProgressPercent(percent);
        progress.setLastCheckpointAt(LocalDateTime.now());
        transferProgressRepository.save(progress);
        log.debug("Saved transfer progress, taskId={}, progressPercent={}, transferredBytes={}, completedFileCount={}",
                taskId, percent, transferredBytes, completedFileCount);
    }

    /**
     * 获取任务已持久化的聚合进度。
     *
     * @param taskId 任务 ID
     * @return 聚合进度
     */
    @Transactional(readOnly = true)
    public TransferProgress getProgress(String taskId) {
        log.debug("Loading transfer progress, taskId={}", taskId);
        return transferProgressRepository.findByTaskId(taskId)
                .orElseThrow(() -> new TransferException("Transfer progress not found: " + taskId));
    }
}
