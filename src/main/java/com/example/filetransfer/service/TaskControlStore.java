package com.example.filetransfer.service;

/**
 * 任务控制信号存储接口。
 * 负责保存暂停、取消等控制信号，支持内存实现和 Redis 实现。
 */
public interface TaskControlStore {

    /**
     * 设置暂停信号。
     *
     * @param taskId 任务 ID
     */
    void requestPause(String taskId);

    /**
     * 设置取消信号。
     *
     * @param taskId 任务 ID
     */
    void requestCancel(String taskId);

    /**
     * 清理任务控制信号。
     *
     * @param taskId 任务 ID
     */
    void clearSignals(String taskId);

    /**
     * 判断是否已请求暂停。
     *
     * @param taskId 任务 ID
     * @return 是否已请求暂停
     */
    boolean isPauseRequested(String taskId);

    /**
     * 判断是否已请求取消。
     *
     * @param taskId 任务 ID
     * @return 是否已请求取消
     */
    boolean isCancelRequested(String taskId);

    /**
     * 查看暂停信号当前快照。
     *
     * @param taskId 任务 ID
     * @return 暂停信号快照
     */
    boolean peekPauseRequested(String taskId);

    /**
     * 查看取消信号当前快照。
     *
     * @param taskId 任务 ID
     * @return 取消信号快照
     */
    boolean peekCancelRequested(String taskId);
}
