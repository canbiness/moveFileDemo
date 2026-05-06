package com.example.filetransfer.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 迁移任务主实体。
 * 用于记录源路径、目标路径、校验模式和任务整体执行状态。
 */
@TableName("transfer_tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferTask {

    /** 任务唯一标识，对外作为接口主键使用。 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 乐观锁版本号，用于避免并发更新覆盖。 */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Long version;

    /** 源目录或源文件的绝对路径。 */
    private String sourcePath;

    /** 目标目录或目标文件的绝对路径。 */
    private String targetPath;

    /** 任务类型，用于区分目录迁移和单文件迁移。 */
    private TransferType transferType;

    /** 任务整体状态。 */
    private TransferStatus status;

    /** 任务总字节数。 */
    private Long totalBytes;

    /** 任务已传输字节数。 */
    private Long transferredBytes;

    /** 任务总文件数。 */
    private Long totalFiles;

    /** 任务总批次数。 */
    private Long totalBatches;

    /** FULL_HASH 模式下实际使用的哈希算法。 */
    private String hashAlgorithm;

    /** 当前任务使用的校验模式。 */
    private VerificationMode verificationMode;

    /** 源端聚合哈希，当前主要作为扩展预留字段。 */
    private String sourceHash;

    /** 目标端聚合哈希，当前主要作为扩展预留字段。 */
    private String targetHash;

    /** 任务级重试计数。 */
    private Integer retryCount;

    /** 最近一次错误信息。 */
    private String lastError;

    /** 任务创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 任务最后更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
