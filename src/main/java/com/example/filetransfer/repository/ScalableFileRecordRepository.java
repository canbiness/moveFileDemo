package com.example.filetransfer.repository;

import com.example.filetransfer.domain.FileTransferStatus;
import com.example.filetransfer.domain.ScalableFileRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 超大规模任务文件明细仓储接口。
 * 提供按批次顺序拉取、状态统计和轻量状态更新能力。
 */
public interface ScalableFileRecordRepository extends JpaRepository<ScalableFileRecord, Long> {

    List<ScalableFileRecord> findTop1000ByTaskIdAndBatchIdAndStatusOrderByIdAsc(String taskId,
                                                                                Long batchId,
                                                                                FileTransferStatus status);

    Page<ScalableFileRecord> findByTaskIdAndBatchIdOrderByIdAsc(String taskId, Long batchId, Pageable pageable);

    Slice<ScalableFileRecord> findByTaskIdAndBatchIdAndStatusInAndIdGreaterThanOrderByIdAsc(String taskId,
                                                                                             Long batchId,
                                                                                             Collection<FileTransferStatus> statuses,
                                                                                             Long id,
                                                                                             Pageable pageable);

    long countByTaskIdAndStatus(String taskId, FileTransferStatus status);

    @Query("""
            select f.status, count(f)
              from ScalableFileRecord f
             where f.taskId = :taskId
             group by f.status
            """)
    List<Object[]> aggregateStatusCountsByTaskId(@Param("taskId") String taskId);

    @Modifying
    @Query("""
            update ScalableFileRecord f
               set f.transferredBytes = :transferredBytes,
                   f.status = :status,
                   f.lastError = :lastError,
                   f.targetHash = :targetHash
             where f.id = :id
            """)
    int updateProgressAndStatus(@Param("id") Long id,
                                @Param("transferredBytes") long transferredBytes,
                                @Param("status") FileTransferStatus status,
                                @Param("lastError") String lastError,
                                @Param("targetHash") String targetHash);
}
