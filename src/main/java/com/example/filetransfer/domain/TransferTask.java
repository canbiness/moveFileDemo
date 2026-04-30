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
 * 迁移任务主实体。
 * 用于记录源路径、目标路径、校验模式和任务整体执行状态。
 */
@Entity
@Table(name = "transfer_tasks", indexes = {
        @Index(name = "idx_transfer_task_status", columnList = "status"),
        @Index(name = "idx_transfer_task_created_at", columnList = "createdAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferTask {

    /** 任务唯一标识，对外作为接口主键使用。 */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** 乐观锁版本号，用于避免并发更新覆盖。 */
    @Version
    private Long version;

    /** 源目录或源文件的绝对路径。 */
    @Column(nullable = false, length = 1024)
    private String sourcePath;

    /** 目标目录或目标文件的绝对路径。 */
    @Column(nullable = false, length = 1024)
    private String targetPath;

    /** 任务类型，用于区分目录迁移和单文件迁移。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransferType transferType;

    /** 任务整体状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransferStatus status;

    /** 任务总字节数。 */
    @Column(nullable = false)
    private Long totalBytes;

    /** 任务已传输字节数。 */
    @Column(nullable = false)
    private Long transferredBytes;

    /** 任务总文件数。 */
    @Column(nullable = false)
    private Long totalFiles;

    /** 任务总批次数。 */
    @Column(nullable = false)
    private Long totalBatches;

    /** FULL_HASH 模式下实际使用的哈希算法。 */
    @Column(length = 128)
    private String hashAlgorithm;

    /** 当前任务使用的校验模式。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VerificationMode verificationMode;

    /** 源端聚合哈希，当前主要作为扩展预留字段。 */
    @Column(length = 128)
    private String sourceHash;

    /** 目标端聚合哈希，当前主要作为扩展预留字段。 */
    @Column(length = 128)
    private String targetHash;

    /** 任务级重试计数。 */
    @Column(nullable = false)
    private Integer retryCount;

    /** 最近一次错误信息。 */
    @Column(length = 2048)
    private String lastError;

    /** 任务创建时间。 */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 任务最后更新时间。 */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
