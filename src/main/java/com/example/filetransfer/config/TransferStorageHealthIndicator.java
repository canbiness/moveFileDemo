package com.example.filetransfer.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 迁移临时目录健康检查。
 * 用于验证断点续传依赖的临时目录是否存在且可写。
 */
@Component("transferStorage")
public class TransferStorageHealthIndicator implements HealthIndicator {

    /** 迁移系统配置。 */
    private final TransferProperties transferProperties;

    /**
     * 构造健康检查组件。
     *
     * @param transferProperties 迁移配置
     */
    public TransferStorageHealthIndicator(TransferProperties transferProperties) {
        this.transferProperties = transferProperties;
    }

    /**
     * 检查临时目录的存在性、目录属性和可写性。
     *
     * @return Spring Boot 健康检查结果
     */
    @Override
    public Health health() {
        Path tempDir = Paths.get(transferProperties.getTempDir()).toAbsolutePath().normalize();
        boolean exists = Files.exists(tempDir);
        boolean directory = exists && Files.isDirectory(tempDir);
        boolean writable = directory && Files.isWritable(tempDir);

        Health.Builder builder = exists && directory && writable ? Health.up() : Health.down();
        return builder
                .withDetail("tempDir", tempDir.toString())
                .withDetail("exists", exists)
                .withDetail("directory", directory)
                .withDetail("writable", writable)
                .build();
    }
}
