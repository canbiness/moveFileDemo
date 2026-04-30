package com.example.filetransfer.domain;

/**
 * 迁移任务校验模式。
 */
public enum VerificationMode {
    /** 仅校验文件大小，吞吐最高。 */
    SIZE_ONLY,
    /** 校验文件大小和修改时间，适合常规大批量迁移。 */
    SIZE_AND_MTIME,
    /** 执行全量哈希校验，最安全但 I/O 开销最大。 */
    FULL_HASH
}
