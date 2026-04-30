package com.example.filetransfer.domain;

/**
 * 批次冷热分层标签。
 * 用于调度阶段区分不同规模和优先级特征的批次。
 */
public enum BatchTemperature {
    /** 小批次，通常希望更快出队并执行完成。 */
    HOT,
    /** 中等规模批次。 */
    WARM,
    /** 大批次，通常需要更谨慎地控制资源占用。 */
    COLD
}
