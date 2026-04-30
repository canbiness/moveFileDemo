package com.example.filetransfer.service;

import com.example.filetransfer.domain.TransferBatch;

import java.util.List;

/**
 * 任务调度热队列存储接口。
 * 用于承载待派发批次的热队列，可由内存或 Redis 实现。
 */
public interface DispatchQueueStore {

    /**
     * 清空指定任务的调度队列。
     *
     * @param taskId 任务 ID
     */
    void clearTask(String taskId);

    /**
     * 将一个批次放入调度热队列。
     *
     * @param taskId 任务 ID
     * @param batch 批次实体
     */
    void enqueueBatch(String taskId, TransferBatch batch);

    /**
     * 按冷热权重从热队列中取出一批待调度批次 ID。
     *
     * @param taskId 任务 ID
     * @param limit 最大返回数量
     * @param hotDispatch HOT 批次每轮派发数量
     * @param warmDispatch WARM 批次每轮派发数量
     * @param coldDispatch COLD 批次每轮派发数量
     * @return 待调度批次 ID 列表
     */
    List<Long> pollBatchIds(String taskId, int limit, int hotDispatch, int warmDispatch, int coldDispatch);
}
