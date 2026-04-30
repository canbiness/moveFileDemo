package com.example.filetransfer.exception;

/**
 * 表示任务在执行过程中收到了取消请求。
 */
public class TaskCancelRequestedException extends RuntimeException {

    /**
     * 使用取消原因创建异常。
     *
     * @param message 取消说明
     */
    public TaskCancelRequestedException(String message) {
        super(message);
    }
}
