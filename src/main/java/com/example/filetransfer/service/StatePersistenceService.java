package com.example.filetransfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.filetransfer.domain.TransferProgress;
import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.TransferTask;
import com.example.filetransfer.exception.TransferException;
import com.example.filetransfer.mapper.TransferProgressMapper;
import com.example.filetransfer.mapper.TransferTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatePersistenceService {

    private final TransferTaskMapper transferTaskMapper;
    private final TransferProgressMapper transferProgressMapper;

    @Transactional
    public TransferTask saveTask(TransferTask task) {
        log.debug("Saving transfer task, taskId={}, status={}", task.getId(), task.getStatus());
        int updated = (task.getId() == null || task.getId().isBlank())
                ? transferTaskMapper.insert(task)
                : transferTaskMapper.updateById(task);
        if (updated == 0) {
            throw new TransferException("Failed to persist transfer task: " + task.getId());
        }
        return task;
    }

    @Transactional
    public void updateTaskStatus(String taskId, TransferStatus status, String lastError) {
        log.debug("Updating transfer task status, taskId={}, status={}, lastError={}", taskId, status, lastError);
        int updated = transferTaskMapper.updateStatusAndError(taskId, status, lastError);
        if (updated == 0) {
            throw new TransferException("Transfer task not found: " + taskId);
        }
    }

    @Transactional(readOnly = true)
    public TransferTask getTask(String taskId) {
        log.debug("Loading transfer task, taskId={}", taskId);
        TransferTask task = transferTaskMapper.selectById(taskId);
        if (task == null) {
            throw new TransferException("Transfer task not found: " + taskId);
        }
        return task;
    }

    @Transactional
    public void saveProgress(String taskId, long totalBytes, long transferredBytes, int fileCount, int completedFileCount) {
        double percent = totalBytes == 0 ? 100.0D : (transferredBytes * 100.0D) / totalBytes;
        TransferProgress progress = transferProgressMapper.selectOne(
                new LambdaQueryWrapper<TransferProgress>().eq(TransferProgress::getTaskId, taskId)
        );
        if (progress == null) {
            progress = TransferProgress.builder().taskId(taskId).build();
        }
        progress.setTotalBytes(totalBytes);
        progress.setTransferredBytes(transferredBytes);
        progress.setFileCount(fileCount);
        progress.setCompletedFileCount(completedFileCount);
        progress.setProgressPercent(percent);
        progress.setLastCheckpointAt(LocalDateTime.now());

        int updated = progress.getId() == null
                ? transferProgressMapper.insert(progress)
                : transferProgressMapper.updateById(progress);
        if (updated == 0) {
            throw new TransferException("Failed to persist transfer progress: " + taskId);
        }
        log.debug("Saved transfer progress, taskId={}, progressPercent={}, transferredBytes={}, completedFileCount={}",
                taskId, percent, transferredBytes, completedFileCount);
    }

    @Transactional
    public void incrementProgress(String taskId, long bytesDelta, int filesDelta) {
        int updated = transferProgressMapper.incrementProgress(taskId, bytesDelta, filesDelta, LocalDateTime.now());
        if (updated == 0) {
            throw new TransferException("Transfer progress not found: " + taskId);
        }
    }

    @Transactional(readOnly = true)
    public TransferProgress getProgress(String taskId) {
        log.debug("Loading transfer progress, taskId={}", taskId);
        TransferProgress progress = transferProgressMapper.selectOne(
                new LambdaQueryWrapper<TransferProgress>().eq(TransferProgress::getTaskId, taskId)
        );
        if (progress == null) {
            throw new TransferException("Transfer progress not found: " + taskId);
        }
        return progress;
    }
}
