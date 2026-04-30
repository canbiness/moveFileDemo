package com.example.filetransfer.repository;

import com.example.filetransfer.domain.BatchStatus;
import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.TransferBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for scalable transfer batches.
 */
public interface TransferBatchRepository extends JpaRepository<TransferBatch, Long> {

    List<TransferBatch> findByTaskIdOrderByBatchNumberAsc(String taskId);

    Page<TransferBatch> findByTaskIdOrderByBatchNumberAsc(String taskId, Pageable pageable);

    Page<TransferBatch> findByTaskIdAndStatusInOrderByBatchNumberAsc(String taskId,
                                                                     Collection<BatchStatus> statuses,
                                                                     Pageable pageable);

    Slice<TransferBatch> findByTaskIdAndStatusInAndBatchNumberGreaterThanOrderByBatchNumberAsc(String taskId,
                                                                                                Collection<BatchStatus> statuses,
                                                                                                Integer batchNumber,
                                                                                                Pageable pageable);

    Optional<TransferBatch> findByTaskIdAndBatchNumber(String taskId, Integer batchNumber);

    long countByTaskIdAndStatus(String taskId, BatchStatus status);

    @Query("""
            select b.status, count(b)
              from TransferBatch b
             where b.taskId = :taskId
             group by b.status
            """)
    List<Object[]> aggregateStatusCountsByTaskId(@Param("taskId") String taskId);

    @Query("""
            select b.temperatureTier, count(b)
              from TransferBatch b
             where b.taskId = :taskId
             group by b.temperatureTier
            """)
    List<Object[]> aggregateTemperatureCountsByTaskId(@Param("taskId") String taskId);

    @Modifying
    @Query("""
            update TransferBatch b
               set b.status = :status,
                   b.transferredBytes = :transferredBytes,
                   b.lastError = :lastError,
                   b.updatedAt = CURRENT_TIMESTAMP,
                   b.version = b.version + 1
             where b.id = :id
            """)
    int updateBatchProgressAndStatus(@Param("id") Long id,
                                     @Param("status") BatchStatus status,
                                     @Param("transferredBytes") long transferredBytes,
                                     @Param("lastError") String lastError);
}
