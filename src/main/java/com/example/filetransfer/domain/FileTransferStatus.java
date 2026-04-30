package com.example.filetransfer.domain;

/**
 * 单文件传输状态枚举。
 * 用于描述批次内某个文件当前的生命周期阶段。
 */
public enum FileTransferStatus {
    /** 文件等待处理。 */
    PENDING,
    /** 文件正在传输。 */
    RUNNING,
    /** 文件传输已暂停，并保留了恢复点。 */
    PAUSED,
    /** 文件传输完成且校验通过。 */
    COMPLETED,
    /** 文件传输失败。 */
    FAILED,
    /** 文件传输被取消。 */
    CANCELED
}
