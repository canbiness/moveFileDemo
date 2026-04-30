package com.example.filetransfer.service;

import com.example.filetransfer.domain.BatchStatus;
import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.ScalableFileRecord;
import com.example.filetransfer.domain.TransferBatch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 超大规模任务批次持久化服务。
 * 批次主记录仍通过 JPA 持久化，文件明细改为 JDBC 批量插入，并在落库后同步预热调度队列。
 */
@Service
@RequiredArgsConstructor
public class ScalableBatchPersistenceService {

    private final DispatchQueueStore dispatchQueueStore;
    private final ScalableFileRecordJdbcService scalableFileRecordJdbcService;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 保存一个批次及其全部文件明细。
     */
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
        entityManager.persist(batch);
        entityManager.flush();

        scalableFileRecordJdbcService.batchInsert(batch.getId(), fileRecords, Math.max(1, Math.min(fileRecords.size(), 1_000)));
        dispatchQueueStore.enqueueBatch(taskId, batch);
        return batch;
    }
}
