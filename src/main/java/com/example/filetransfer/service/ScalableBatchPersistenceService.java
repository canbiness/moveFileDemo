package com.example.filetransfer.service;

import com.example.filetransfer.domain.BatchStatus;
import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.ScalableFileRecord;
import com.example.filetransfer.domain.TransferBatch;
import com.example.filetransfer.mapper.TransferBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScalableBatchPersistenceService {

    private final DispatchQueueStore dispatchQueueStore;
    private final ScalableFileRecordJdbcService scalableFileRecordJdbcService;
    private final TransferBatchMapper transferBatchMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransferBatch persistBatch(String taskId,
                                      int batchNumber,
                                      int fileCount,
                                      long totalBytes,
                                      List<ScalableFileRecord> fileRecords,
                                      BatchTemperature temperatureTier,
                                      int schedulingPriority) {
        TransferBatch batch = TransferBatch.builder()
                .taskId(taskId)
                .batchNumber(batchNumber)
                .status(BatchStatus.SCANNED)
                .temperatureTier(temperatureTier)
                .schedulingPriority(schedulingPriority)
                .fileCount(fileCount)
                .totalBytes(totalBytes)
                .transferredBytes(0L)
                .build();
        int inserted = transferBatchMapper.insert(batch);
        if (inserted == 0) {
            throw new IllegalStateException("Failed to persist transfer batch");
        }

        scalableFileRecordJdbcService.batchInsert(batch.getId(), fileRecords, Math.max(1, Math.min(fileRecords.size(), 1_000)));
        dispatchQueueStore.enqueueBatch(taskId, batch);
        return batch;
    }
}
