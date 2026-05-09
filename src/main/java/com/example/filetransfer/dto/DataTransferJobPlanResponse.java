package com.example.filetransfer.dto;

import com.example.filetransfer.domain.TransferStatus;

public record DataTransferJobPlanResponse(
        Long jobId,
        Long ruleId,
        String sourcePath,
        String targetPath,
        String taskId,
        TransferStatus status,
        String message
) {
}
