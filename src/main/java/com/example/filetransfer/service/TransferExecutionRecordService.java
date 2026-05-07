package com.example.filetransfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.filetransfer.domain.TransferExecutionRecord;
import com.example.filetransfer.domain.TransferProgress;
import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.TransferTask;
import com.example.filetransfer.exception.TransferException;
import com.example.filetransfer.mapper.TransferExecutionRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferExecutionRecordService {

    private final TransferExecutionRecordMapper transferExecutionRecordMapper;
    private final StatePersistenceService statePersistenceService;

    @Transactional
    public void recordExecution(String taskId,
                                LocalDateTime startedAt,
                                long startingTransferredBytes,
                                long startingCompletedFiles,
                                TransferStatus status,
                                String lastError) {
        TransferTask task = statePersistenceService.getTask(taskId);
        TransferProgress progress = statePersistenceService.getProgress(taskId);
        LocalDateTime finishedAt = LocalDateTime.now();

        long movedBytes = Math.max(0L, progress.getTransferredBytes() - startingTransferredBytes);
        long movedFiles = Math.max(0L, progress.getCompletedFileCount() - startingCompletedFiles);
        long durationMillis = Math.max(0L, Duration.between(startedAt, finishedAt).toMillis());

        TransferExecutionRecord record = TransferExecutionRecord.builder()
                .taskId(taskId)
                .taskName(resolveTaskName(task))
                .scannedFileCount(resolveScannedFileCount(task, progress))
                .movedFileCount(movedFiles)
                .movedFileSize(movedBytes)
                .startedAt(startedAt)
                .durationMillis(durationMillis)
                .status(status)
                .lastError(lastError)
                .build();

        int inserted = transferExecutionRecordMapper.insert(record);
        if (inserted == 0) {
            throw new TransferException("Failed to persist transfer execution record: " + taskId);
        }
        log.info("Persisted transfer execution record, taskId={}, status={}, durationMillis={}, movedFiles={}, movedBytes={}",
                taskId, status, durationMillis, movedFiles, movedBytes);
    }

    @Transactional(readOnly = true)
    public TransferExecutionRecord latestByTaskId(String taskId) {
        TransferExecutionRecord record = transferExecutionRecordMapper.selectOne(
                new LambdaQueryWrapper<TransferExecutionRecord>()
                        .eq(TransferExecutionRecord::getTaskId, taskId)
                        .orderByDesc(TransferExecutionRecord::getCreatedAt)
                        .last("limit 1")
        );
        if (record == null) {
            throw new TransferException("Transfer execution record not found: " + taskId);
        }
        return record;
    }

    private String resolveTaskName(TransferTask task) {
        if (task.getTaskName() != null && !task.getTaskName().isBlank()) {
            return task.getTaskName();
        }
        Path sourcePath = Path.of(task.getSourcePath());
        Path fileName = sourcePath.getFileName();
        if (fileName != null) {
            return fileName.toString();
        }
        return task.getSourcePath();
    }

    private long resolveScannedFileCount(TransferTask task, TransferProgress progress) {
        if (task.getTotalFiles() != null) {
            return task.getTotalFiles();
        }
        return progress.getFileCount() == null ? 0L : progress.getFileCount();
    }
}
