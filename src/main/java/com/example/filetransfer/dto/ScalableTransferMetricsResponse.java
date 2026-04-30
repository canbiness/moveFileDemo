package com.example.filetransfer.dto;

import com.example.filetransfer.domain.BatchStatus;
import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.FileTransferStatus;
import com.example.filetransfer.domain.ThrottleReason;
import com.example.filetransfer.domain.TransferStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务运行态指标响应。
 *
 * @param taskId 任务 ID
 * @param status 任务状态
 * @param totalBatches 任务总批次数
 * @param totalFiles 任务总文件数
 * @param completedFiles 已完成文件数
 * @param totalBytes 任务总字节数
 * @param transferredBytes 已传输字节数
 * @param progressPercent 任务进度百分比
 * @param lastCheckpointAt 最近一次进度检查点时间
 * @param pauseRequested 当前是否收到暂停请求
 * @param cancelRequested 当前是否收到取消请求
 * @param batchStatusCounts 批次状态分布
 * @param batchTemperatureCounts 批次冷热分层分布
 * @param fileStatusCounts 文件状态分布
 * @param schedulingStrategy 当前调度策略名称
 * @param backpressureActive 当前是否处于背压状态
 * @param inFlightBatches 当前在途批次数
 * @param inFlightFiles 当前在途文件数
 * @param inFlightBytes 当前在途字节数
 * @param retryCount 当前任务累计重试次数
 * @param throttledDispatchCount 调度被背压拦截的累计次数
 * @param inFlightTemperatureCounts 在途批次冷热分层分布
 * @param throttleReasonCounts 背压原因累计分布
 * @param transferExecutor 文件执行线程池快照
 * @param coordinatorExecutor 协调线程池快照
 */
public record ScalableTransferMetricsResponse(
        String taskId,
        TransferStatus status,
        long totalBatches,
        long totalFiles,
        long completedFiles,
        long totalBytes,
        long transferredBytes,
        double progressPercent,
        LocalDateTime lastCheckpointAt,
        boolean pauseRequested,
        boolean cancelRequested,
        Map<BatchStatus, Long> batchStatusCounts,
        Map<BatchTemperature, Long> batchTemperatureCounts,
        Map<FileTransferStatus, Long> fileStatusCounts,
        String schedulingStrategy,
        boolean backpressureActive,
        long inFlightBatches,
        long inFlightFiles,
        long inFlightBytes,
        long retryCount,
        long throttledDispatchCount,
        Map<BatchTemperature, Long> inFlightTemperatureCounts,
        Map<ThrottleReason, Long> throttleReasonCounts,
        ExecutorMetricsResponse transferExecutor,
        ExecutorMetricsResponse coordinatorExecutor
) {
}
