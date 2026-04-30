package com.example.filetransfer.repository;

import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.TransferTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for transfer-task records.
 */
public interface TransferTaskRepository extends JpaRepository<TransferTask, String> {

    @Modifying
    @Query("""
            update TransferTask t
               set t.status = :status,
                   t.lastError = :lastError,
                   t.updatedAt = CURRENT_TIMESTAMP,
                   t.version = t.version + 1
             where t.id = :taskId
            """)
    int updateStatusAndError(@Param("taskId") String taskId,
                             @Param("status") TransferStatus status,
                             @Param("lastError") String lastError);
}
