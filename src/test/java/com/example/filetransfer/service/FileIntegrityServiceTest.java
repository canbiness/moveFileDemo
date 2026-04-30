package com.example.filetransfer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 文件完整性校验服务单元测试，验证摘要计算和哈希比对逻辑。
 */
class FileIntegrityServiceTest {

    private final FileIntegrityService fileIntegrityService;

    FileIntegrityServiceTest() {
        com.example.filetransfer.config.TransferProperties transferProperties = new com.example.filetransfer.config.TransferProperties();
        transferProperties.setBufferSize(1024 * 1024);
        this.fileIntegrityService = new FileIntegrityService(transferProperties);
    }

    @TempDir
    Path tempDir;

    /**
     * 相同内容应始终生成相同摘要，并能够通过哈希校验。
     */
    @Test
    void shouldCalculateSameHashForSameContent() throws Exception {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "hello world");
        Files.writeString(file2, "hello world");

        String hash1 = fileIntegrityService.calculateHash(file1, "SHA-256");
        String hash2 = fileIntegrityService.calculateHash(file2, "SHA-256");

        assertEquals(hash1, hash2);
        assertDoesNotThrow(() -> fileIntegrityService.validateHash(file1, file2, "SHA-256"));
    }

    /**
     * 不同内容必须生成不同摘要。
     */
    @Test
    void shouldReturnDifferentHashForDifferentContent() throws Exception {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "alpha");
        Files.writeString(file2, "beta");

        assertNotEquals(
                fileIntegrityService.calculateHash(file1, "MD5"),
                fileIntegrityService.calculateHash(file2, "MD5")
        );
    }
}
