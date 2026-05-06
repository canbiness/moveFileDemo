package com.example.filetransfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.filetransfer.domain.TransferProgress;
import com.example.filetransfer.exception.TransferException;
import com.example.filetransfer.mapper.TransferProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ScalableProgressService {

    private final TransferProgressMapper transferProgressMapper;

    @Transactional
    public void initialize(String taskId, long totalBytes, long totalFiles) {
        TransferProgress progress = transferProgressMapper.selectOne(
                new LambdaQueryWrapper<TransferProgress>().eq(TransferProgress::getTaskId, taskId)
        );
        if (progress == null) {
            progress = TransferProgress.builder().taskId(taskId).build();
        }
        progress.setTotalBytes(totalBytes);
        progress.setTransferredBytes(0L);
        progress.setFileCount(Math.toIntExact(totalFiles));
        progress.setCompletedFileCount(0);
        progress.setProgressPercent(0D);
        progress.setLastCheckpointAt(LocalDateTime.now());

        int updated = progress.getId() == null
                ? transferProgressMapper.insert(progress)
                : transferProgressMapper.updateById(progress);
        if (updated == 0) {
            throw new TransferException("Failed to persist transfer progress: " + taskId);
        }
    }

    @Transactional
    public void complete(String taskId) {
        TransferProgress progress = getProgress(taskId);
        progress.setTransferredBytes(progress.getTotalBytes());
        progress.setCompletedFileCount(progress.getFileCount());
        progress.setProgressPercent(100D);
        progress.setLastCheckpointAt(LocalDateTime.now());
        int updated = transferProgressMapper.updateById(progress);
        if (updated == 0) {
            throw new TransferException("Failed to persist transfer progress: " + taskId);
        }
    }

    @Transactional
    public void increment(String taskId, long bytesDelta, int filesDelta) {
        int updated = transferProgressMapper.incrementProgress(taskId, bytesDelta, filesDelta, LocalDateTime.now());
        if (updated == 0) {
            throw new TransferException("Transfer progress not found: " + taskId);
        }
    }

    @Transactional(readOnly = true)
    public TransferProgress getProgress(String taskId) {
        TransferProgress progress = transferProgressMapper.selectOne(
                new LambdaQueryWrapper<TransferProgress>().eq(TransferProgress::getTaskId, taskId)
        );
        if (progress == null) {
            throw new TransferException("Transfer progress not found: " + taskId);
        }
        return progress;
    }
}
