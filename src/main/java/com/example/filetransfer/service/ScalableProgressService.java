package com.example.filetransfer.service;

import com.example.filetransfer.domain.TransferProgress;
import com.example.filetransfer.exception.TransferException;
import com.example.filetransfer.repository.TransferProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 大规模任务进度服务。
 * 规划阶段负责初始化，执行阶段按增量推进，避免频繁做全量聚合。
 */
@Service
@RequiredArgsConstructor
public class ScalableProgressService {

    /** 聚合进度仓库。 */
    private final TransferProgressRepository transferProgressRepository;

    /**
     * 初始化任务进度快照。
     *
     * @param taskId 任务 ID
     * @param totalBytes 总字节数
     * @param totalFiles 总文件数
     */
    @Transactional
    public void initialize(String taskId, long totalBytes, long totalFiles) {
        TransferProgress progress = transferProgressRepository.findByTaskId(taskId)
                .orElse(TransferProgress.builder().taskId(taskId).build());
        // 初始化时总量已知，但实际传输尚未开始，因此已传输值和完成文件数都清零。
        progress.setTotalBytes(totalBytes);
        progress.setTransferredBytes(0L);
        progress.setFileCount(Math.toIntExact(totalFiles));
        progress.setCompletedFileCount(0);
        progress.setProgressPercent(0D);
        progress.setLastCheckpointAt(LocalDateTime.now());
        transferProgressRepository.save(progress);
    }

    /**
     * 直接把任务进度推进到完成状态。
     *
     * @param taskId 任务 ID
     */
    @Transactional
    public void complete(String taskId) {
        TransferProgress progress = getProgress(taskId);
        // 完成态下，已传输字节和完成文件数都直接补齐到总量。
        progress.setTransferredBytes(progress.getTotalBytes());
        progress.setCompletedFileCount(progress.getFileCount());
        progress.setProgressPercent(100D);
        progress.setLastCheckpointAt(LocalDateTime.now());
        transferProgressRepository.save(progress);
    }

    /**
     * 按增量推进任务进度。
     *
     * @param taskId 任务 ID
     * @param bytesDelta 字节增量
     * @param filesDelta 文件数增量
     */
    @Transactional
    public void increment(String taskId, long bytesDelta, int filesDelta) {
        int updated = transferProgressRepository.incrementProgress(taskId, bytesDelta, filesDelta, LocalDateTime.now());
        if (updated == 0) {
            throw new TransferException("Transfer progress not found: " + taskId);
        }
    }

    /**
     * 查询任务进度快照。
     *
     * @param taskId 任务 ID
     * @return 聚合进度
     */
    @Transactional(readOnly = true)
    public TransferProgress getProgress(String taskId) {
        return transferProgressRepository.findByTaskId(taskId)
                .orElseThrow(() -> new TransferException("Transfer progress not found: " + taskId));
    }
}
