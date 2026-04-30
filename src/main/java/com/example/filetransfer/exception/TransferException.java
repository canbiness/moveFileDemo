package com.example.filetransfer.exception;

/**
 * 文件迁移领域统一业务异常。
 * 用于表达规划、扫描、复制、校验、调度和恢复过程中的业务错误。
 */
public class TransferException extends RuntimeException {

    /**
     * 使用错误消息创建业务异常。
     *
     * @param message 错误消息
     */
    public TransferException(String message) {
        super(message);
    }

    /**
     * 使用错误消息和根因创建业务异常。
     *
     * @param message 错误消息
     * @param cause 原始异常
     */
    public TransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
