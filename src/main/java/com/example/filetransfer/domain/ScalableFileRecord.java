package com.example.filetransfer.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 面向超大规模文件场景的轻量文件明细实体。
 * 只保留调度和恢复所必需的信息。
 */
@TableName("scalable_file_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalableFileRecord {

    /** 文件记录主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属任务 ID。 */
    private String taskId;

    /** 所属批次 ID。 */
    private Long batchId;

    /** 相对路径。 */
    private String relativePath;

    /** 源文件大小。 */
    private Long sourceSize;

    /** 已传输字节数。 */
    private Long transferredBytes;

    /** 源文件最后修改时间的毫秒值。 */
    private Long sourceLastModifiedMillis;

    /** 文件记录状态。 */
    private FileTransferStatus status;

    /** 源文件哈希值，主要为扩展预留。 */
    private String sourceHash;

    /** 目标文件哈希值。 */
    private String targetHash;

    /** 最近一次文件错误信息。 */
    private String lastError;
}
