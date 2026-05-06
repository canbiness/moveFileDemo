package com.example.filetransfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 文件迁移系统 Spring Boot 启动类。
 */
@SpringBootApplication(exclude = RedisRepositoriesAutoConfiguration.class)
@MapperScan("com.example.filetransfer.mapper")
public class FileTransferApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FileTransferApplication.class, args);
    }
}
