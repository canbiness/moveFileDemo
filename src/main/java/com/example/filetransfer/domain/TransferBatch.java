package com.example.filetransfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 大规模迁移任务的批次实体。
 * 一个任务会被拆成多个批次，以便分批调度和恢复。
 */
@Entity
@Table(name = "transfer_batches", indexes = {
        @Index(name = "idx_transfer_batch_task_batch", columnList = "taskId,batchNumber"),
        @Index(name = "idx_transfer_batch_task_status", columnList = "taskId,status"),
        @Index(name = "idx_transfer_batch_task_status_batch", columnList = "taskId,status,batchNumber"),
        @Index(name = "idx_transfer_batch_task_temp_priority", columnList = "taskId,temperatureTier,schedulingPriority")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_transfer_batch_task_batch", columnNames = {"taskId", "batchNumber"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferBatch {

    /** 批次主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 乐观锁版本号。 */
    @Version
    private Long version;

    /** 所属任务 ID。 */
    @Column(nullable = false)
    private String taskId;

    /** 批次序号，从 1 开始递增。 */
    @Column(nullable = false)
    private Integer batchNumber;

    /** 批次状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BatchStatus status;

    /** 批次冷热分层标签。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BatchTemperature temperatureTier;

    /** 调度优先级，数值越小通常越优先。 */
    @Column(nullable = false)
    private Integer schedulingPriority;

    /** 批次内文件数量。 */
    @Column(nullable = false)
    private Integer fileCount;

    /** 批次总字节数。 */
    @Column(nullable = false)
    private Long totalBytes;

    /** 批次已传输字节数。 */
    @Column(nullable = false)
    private Long transferredBytes;

    /** 最近一次批次错误信息。 */
    @Column(length = 2048)
    private String lastError;

    /** 批次创建时间。 */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 批次最后更新时间。 */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
