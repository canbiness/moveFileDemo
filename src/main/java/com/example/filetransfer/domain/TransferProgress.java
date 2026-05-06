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
 * 任务聚合进度实体。
 * 用于存储任务级别的总字节数、已传输字节数和完成文件数。
 */
@TableName("transfer_progress")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferProgress {

    /** 聚合进度主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 乐观锁版本号。 */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Long version;

    /** 所属任务 ID。 */
    private String taskId;

    /** 任务总字节数。 */
    private Long totalBytes;

    /** 当前已传输字节数。 */
    private Long transferredBytes;

    /** 任务总文件数。 */
    private Integer fileCount;

    /** 当前已完成文件数。 */
    private Integer completedFileCount;

    /** 进度百分比。 */
    private Double progressPercent;

    /** 最近一次进度检查点时间。 */
    private LocalDateTime lastCheckpointAt;

    /** 进度记录创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 进度记录最后更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
