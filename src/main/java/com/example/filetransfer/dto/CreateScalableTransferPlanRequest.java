package com.example.filetransfer.dto;

import com.example.filetransfer.domain.VerificationMode;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建大规模迁移计划请求。
 *
 * @param sourcePath 源目录绝对路径
 * @param targetPath 目标目录绝对路径
 * @param verificationMode 校验模式，允许为空，服务端会回退到默认模式
 */
public record CreateScalableTransferPlanRequest(
        @NotBlank String sourcePath,
        @NotBlank String targetPath,
        VerificationMode verificationMode
) {
}
