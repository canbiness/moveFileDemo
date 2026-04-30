package com.example.filetransfer.dto;

import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.VerificationMode;

/**
 * 创建迁移计划后的响应。
 *
 * @param taskId 任务 ID
 * @param status 任务当前状态
 * @param totalFiles 当前已统计到的文件总数
 * @param totalBytes 当前已统计到的字节总数
 * @param batchCount 当前已生成的批次数
 * @param configuredBatchFileCount 配置中的单批次文件数上限
 * @param configuredBatchBytes 配置中的单批次字节数上限
 * @param verificationMode 实际采用的校验模式
 * @param recommendation 给客户端的使用建议
 */
public record ScalableTransferPlanResponse(
        String taskId,
        TransferStatus status,
        long totalFiles,
        long totalBytes,
        long batchCount,
        int configuredBatchFileCount,
        long configuredBatchBytes,
        VerificationMode verificationMode,
        String recommendation
) {
}
