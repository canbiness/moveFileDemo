package com.example.filetransfer.dto;

import com.example.filetransfer.domain.TransferStatus;

/**
 * 迁移动作响应。
 * 常用于执行、暂停、恢复、取消等状态推进接口。
 *
 * @param taskId 任务 ID
 * @param status 动作触发后的任务状态
 * @param message 动作响应说明
 */
public record ScalableTransferActionResponse(
        String taskId,
        TransferStatus status,
        String message
) {
}
