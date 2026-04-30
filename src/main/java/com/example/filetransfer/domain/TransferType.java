package com.example.filetransfer.domain;

/**
 * 迁移任务类型枚举。
 * 用于区分任务的源路径是单文件还是目录树。
 */
public enum TransferType {
    /** 源路径指向单个文件。 */
    FILE,
    /** 源路径指向目录。 */
    DIRECTORY
}
