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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 面向超大规模文件场景的轻量文件明细实体。
 * 只保留调度和恢复所必需的信息。
 */
@Entity
@Table(name = "scalable_file_records", indexes = {
        @Index(name = "idx_scalable_file_task_batch", columnList = "taskId,batchId"),
        @Index(name = "idx_scalable_file_status", columnList = "status"),
        @Index(name = "idx_scalable_file_task_status", columnList = "taskId,status"),
        @Index(name = "idx_scalable_file_task_batch_status_id", columnList = "taskId,batchId,status,id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalableFileRecord {

    /** 文件记录主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属任务 ID。 */
    @Column(nullable = false)
    private String taskId;

    /** 所属批次 ID。 */
    @Column(nullable = false)
    private Long batchId;

    /** 相对路径。 */
    @Column(nullable = false, length = 1024)
    private String relativePath;

    /** 源文件大小。 */
    @Column(nullable = false)
    private Long sourceSize;

    /** 已传输字节数。 */
    @Column(nullable = false)
    private Long transferredBytes;

    /** 源文件最后修改时间的毫秒值。 */
    @Column(nullable = false)
    private Long sourceLastModifiedMillis;

    /** 文件记录状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FileTransferStatus status;

    /** 源文件哈希值，主要为扩展预留。 */
    @Column(length = 128)
    private String sourceHash;

    /** 目标文件哈希值。 */
    @Column(length = 128)
    private String targetHash;

    /** 最近一次文件错误信息。 */
    @Column(length = 2048)
    private String lastError;
}
