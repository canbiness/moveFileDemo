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
 * 大规模迁移任务的批次实体。
 * 一个任务会被拆成多个批次，以便分批调度和恢复。
 */
@TableName("transfer_batches")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferBatch {

    /** 批次主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 乐观锁版本号。 */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Long version;

    /** 所属任务 ID。 */
    private String taskId;

    /** 批次序号，从 1 开始递增。 */
    private Integer batchNumber;

    /** 批次状态。 */
    private BatchStatus status;

    /** 批次冷热分层标签。 */
    private BatchTemperature temperatureTier;

    /** 调度优先级，数值越小通常越优先。 */
    private Integer schedulingPriority;

    /** 批次内文件数量。 */
    private Integer fileCount;

    /** 批次总字节数。 */
    private Long totalBytes;

    /** 批次已传输字节数。 */
    private Long transferredBytes;

    /** 最近一次批次错误信息。 */
    private String lastError;

    /** 批次创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 批次最后更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
