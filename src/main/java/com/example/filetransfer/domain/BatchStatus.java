package com.example.filetransfer.domain;

/**
 * 批次状态枚举。
 * 用于描述一个迁移任务中的批次当前处于哪个执行阶段。
 */
public enum BatchStatus {
    /** 批次已创建，但尚未完成规划或装载。 */
    PENDING,
    /** 批次已扫描并落库，等待调度执行。 */
    SCANNED,
    /** 批次正在执行。 */
    RUNNING,
    /** 批次已暂停，可后续恢复。 */
    PAUSED,
    /** 批次已成功完成。 */
    COMPLETED,
    /** 批次执行失败，可根据策略重试。 */
    FAILED,
    /** 批次已被取消。 */
    CANCELED
}
