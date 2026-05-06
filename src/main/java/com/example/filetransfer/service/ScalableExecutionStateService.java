package com.example.filetransfer.service;

import com.example.filetransfer.domain.BatchStatus;
import com.example.filetransfer.domain.FileTransferStatus;
import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.mapper.TransferBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScalableExecutionStateService {

    private final StatePersistenceService statePersistenceService;
    private final TransferBatchMapper transferBatchMapper;
    private final ScalableFileStatusBufferService scalableFileStatusBufferService;

    @Transactional
    public void updateTaskStatus(String taskId, TransferStatus status, String lastError) {
        statePersistenceService.updateTaskStatus(taskId, status, lastError);
    }

    @Transactional
    public void updateBatchStatus(Long batchId, BatchStatus status, long transferredBytes, String lastError) {
        int updated = transferBatchMapper.updateBatchProgressAndStatus(batchId, status, transferredBytes, lastError);
        if (updated == 0) {
            throw new IllegalStateException("Batch not found: " + batchId);
        }
    }

    public void updateFileStatus(Long recordId,
                                 String taskId,
                                 FileTransferStatus status,
                                 long transferredBytes,
                                 String lastError,
                                 String targetHash) {
        scalableFileStatusBufferService.enqueue(taskId, recordId, status, transferredBytes, lastError, targetHash);
    }

    public void flushBufferedFileStatuses(String taskId) {
        scalableFileStatusBufferService.flushTask(taskId);
    }
}
