package com.example.filetransfer.service;

import com.example.filetransfer.domain.FileTransferStatus;

/**
 * 文件状态批量更新命令。
 *
 * @param taskId 任务 ID
 * @param recordId 文件记录 ID
 * @param status 文件状态
 * @param transferredBytes 已传输字节数
 * @param lastError 最近一次错误信息
 * @param targetHash 目标文件哈希值
 */
public record FileStatusUpdateCommand(String taskId,
                                      Long recordId,
                                      FileTransferStatus status,
                                      long transferredBytes,
                                      String lastError,
                                      String targetHash) {
}
