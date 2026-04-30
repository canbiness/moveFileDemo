package com.example.filetransfer.repository;

import com.example.filetransfer.domain.TransferProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 任务聚合进度仓储接口，负责查询和持久化任务级进度快照。
 */
public interface TransferProgressRepository extends JpaRepository<TransferProgress, Long> {

    Optional<TransferProgress> findByTaskId(String taskId);

    @Modifying
    @Query("""
            update TransferProgress p
               set p.transferredBytes = p.transferredBytes + :bytesDelta,
                   p.completedFileCount = p.completedFileCount + :filesDelta,
                   p.progressPercent = case
                       when p.totalBytes = 0 then 100
                       else ((p.transferredBytes + :bytesDelta) * 100.0 / p.totalBytes)
                   end,
                   p.lastCheckpointAt = :checkpoint
             where p.taskId = :taskId
            """)
    int incrementProgress(@Param("taskId") String taskId,
                          @Param("bytesDelta") long bytesDelta,
                          @Param("filesDelta") int filesDelta,
                          @Param("checkpoint") LocalDateTime checkpoint);
}
