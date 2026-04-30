package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.exception.TransferException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 文件完整性校验服务，负责文件大小校验和哈希摘要计算。
 */
@Service
@RequiredArgsConstructor
public class FileIntegrityService {

    private final TransferProperties transferProperties;
    private final ThreadLocal<byte[]> hashBufferCache = new ThreadLocal<>();

    /**
     * 使用指定算法计算文件摘要。
     */
    public String calculateHash(Path path, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream inputStream = Files.newInputStream(path)) {
                byte[] buffer = acquireBuffer();
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new TransferException("Failed to calculate hash for: " + path, ex);
        }
    }

    /**
     * 校验源文件与目标文件的大小是否一致。
     */
    public void validateSize(Path source, Path target) {
        try {
            long sourceSize = Files.size(source);
            long targetSize = Files.size(target);
            if (sourceSize != targetSize) {
                throw new TransferException("File size mismatch. source=" + sourceSize + ", target=" + targetSize);
            }
        } catch (IOException ex) {
            throw new TransferException("Failed to validate file size", ex);
        }
    }

    /**
     * 校验源文件与目标文件的哈希值是否一致。
     */
    public void validateHash(Path source, Path target, String algorithm) {
        String sourceHash = calculateHash(source, algorithm);
        String targetHash = calculateHash(target, algorithm);
        if (!sourceHash.equals(targetHash)) {
            throw new TransferException("Hash mismatch detected");
        }
    }

    private byte[] acquireBuffer() {
        int bufferSize = transferProperties.getBufferSize();
        byte[] buffer = hashBufferCache.get();
        if (buffer == null || buffer.length != bufferSize) {
            buffer = new byte[bufferSize];
            hashBufferCache.set(buffer);
        }
        return buffer;
    }
}
