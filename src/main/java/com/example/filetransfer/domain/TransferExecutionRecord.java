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

@TableName("transfer_execution_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferExecutionRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Version
    @TableField(fill = FieldFill.INSERT)
    private Long version;

    private String taskId;

    private String taskName;

    private Long scannedFileCount;

    private Long movedFileCount;

    private Long movedFileSize;

    private LocalDateTime startedAt;

    private Long durationMillis;

    private TransferStatus status;

    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
