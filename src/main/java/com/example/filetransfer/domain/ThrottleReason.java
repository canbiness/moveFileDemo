package com.example.filetransfer.domain;

/**
 * 背压触发原因枚举。
 * 用于标识调度器为什么暂时不继续派发新的批次。
 */
public enum ThrottleReason {

    /** 执行线程池活跃度过高。 */
    THREAD_POOL,

    /** 执行队列积压过高。 */
    QUEUE,

    /** 当前任务的在途字节数过高。 */
    IN_FLIGHT_BYTES,

    /** 冷批次并发数达到上限。 */
    COLD_LIMIT
}
