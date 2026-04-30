package com.example.filetransfer.exception;

/**
 * 表示任务在执行过程中收到了暂停请求。
 */
public class TaskPauseRequestedException extends RuntimeException {

    /**
     * 使用暂停原因创建异常。
     *
     * @param message 暂停说明
     */
    public TaskPauseRequestedException(String message) {
        super(message);
    }
}
