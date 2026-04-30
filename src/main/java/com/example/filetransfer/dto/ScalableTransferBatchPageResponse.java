package com.example.filetransfer.dto;

import java.util.List;

/**
 * 批次分页响应。
 *
 * @param taskId 所属任务 ID
 * @param page 当前页码
 * @param size 当前页大小
 * @param totalElements 批次总数
 * @param totalPages 总页数
 * @param batches 当前页批次列表
 */
public record ScalableTransferBatchPageResponse(
        String taskId,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ScalableTransferBatchResponse> batches
) {
}
