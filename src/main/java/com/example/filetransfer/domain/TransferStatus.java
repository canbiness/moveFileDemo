package com.example.filetransfer.domain;

/**
 * 迁移任务生命周期状态。
 */
public enum TransferStatus {
    /** 任务已创建，但还未进入规划流程。 */
    CREATED,
    /** 任务正在后台规划。 */
    PLANNING,
    /** 任务规划完成，可进入执行阶段。 */
    PLANNED,
    /** 任务正在执行。 */
    RUNNING,
    /** 任务已暂停。 */
    PAUSED,
    /** 任务已完成。 */
    COMPLETED,
    /** 任务执行失败。 */
    FAILED,
    /** 任务已取消。 */
    CANCELED
}
