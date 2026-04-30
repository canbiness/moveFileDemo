package com.example.filetransfer.dto;

import com.example.filetransfer.domain.BatchStatus;
import com.example.filetransfer.domain.BatchTemperature;

import java.time.LocalDateTime;

/**
 * 单个批次的查询响应。
 *
 * @param batchId 批次主键
 * @param batchNumber 批次序号
 * @param status 批次状态
 * @param temperatureTier 批次冷热分层标签
 * @param schedulingPriority 批次调度优先级
 * @param fileCount 批次文件数
 * @param totalBytes 批次总字节数
 * @param transferredBytes 批次已传输字节数
 * @param lastError 最近一次错误信息
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 */
public record ScalableTransferBatchResponse(
        Long batchId,
        Integer batchNumber,
        BatchStatus status,
        BatchTemperature temperatureTier,
        Integer schedulingPriority,
        Integer fileCount,
        Long totalBytes,
        Long transferredBytes,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
