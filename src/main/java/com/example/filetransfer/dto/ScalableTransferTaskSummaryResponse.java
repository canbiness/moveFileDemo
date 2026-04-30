package com.example.filetransfer.dto;

import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.VerificationMode;

import java.time.LocalDateTime;

/**
 * 任务摘要响应。
 *
 * @param taskId 任务 ID
 * @param sourcePath 源路径
 * @param targetPath 目标路径
 * @param status 任务状态
 * @param verificationMode 校验模式
 * @param totalFiles 总文件数
 * @param totalBatches 总批次数
 * @param totalBytes 总字节数
 * @param transferredBytes 已传输字节数
 * @param progressPercent 进度百分比
 * @param hashAlgorithm 哈希算法
 * @param lastError 最近一次错误信息
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 */
public record ScalableTransferTaskSummaryResponse(
        String taskId,
        String sourcePath,
        String targetPath,
        TransferStatus status,
        VerificationMode verificationMode,
        long totalFiles,
        long totalBatches,
        long totalBytes,
        long transferredBytes,
        double progressPercent,
        String hashAlgorithm,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
