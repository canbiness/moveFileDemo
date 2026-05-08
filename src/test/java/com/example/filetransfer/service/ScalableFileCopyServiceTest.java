package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.domain.FileTransferStatus;
import com.example.filetransfer.domain.ScalableFileRecord;
import com.example.filetransfer.domain.VerificationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScalableFileCopyServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDeleteSourceFileAfterFreshCopy() throws Exception {
        TransferProperties properties = buildProperties();
        ScalableFileCopyService service = new ScalableFileCopyService(
                properties,
                new FileIntegrityService(properties)
        );

        Path sourceRoot = tempDir.resolve("source");
        Path targetRoot = tempDir.resolve("target");
        Path nestedDir = sourceRoot.resolve("nested/inner");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(targetRoot);
        Files.createDirectories(nestedDir);
        Path sourceFile = nestedDir.resolve("sample.txt");
        Files.writeString(sourceFile, "move-me");

        ScalableFileRecord record = buildRecord("nested/inner/sample.txt", sourceFile);

        service.copyFile(
                sourceRoot,
                targetRoot,
                VerificationMode.SIZE_AND_MTIME,
                record,
                (bytesDelta, absoluteTransferred) -> {
                }
        );

        assertFalse(Files.exists(sourceFile));
        assertFalse(Files.exists(nestedDir));
        assertFalse(Files.exists(sourceRoot.resolve("nested")));
        assertTrue(Files.exists(targetRoot.resolve("nested/inner/sample.txt")));
        assertEquals("move-me", Files.readString(targetRoot.resolve("nested/inner/sample.txt")));
    }

    @Test
    void shouldKeepNonEmptyParentsWhenDeletingSourceFile() throws Exception {
        TransferProperties properties = buildProperties();
        ScalableFileCopyService service = new ScalableFileCopyService(
                properties,
                new FileIntegrityService(properties)
        );

        Path sourceRoot = tempDir.resolve("source-non-empty");
        Path targetRoot = tempDir.resolve("target-non-empty");
        Path nestedDir = sourceRoot.resolve("nested/inner");
        Files.createDirectories(nestedDir);
        Files.createDirectories(targetRoot);
        Path firstFile = nestedDir.resolve("first.txt");
        Path secondFile = nestedDir.resolve("second.txt");
        Files.writeString(firstFile, "first");
        Files.writeString(secondFile, "second");

        ScalableFileRecord record = buildRecord("nested/inner/first.txt", firstFile);

        service.copyFile(
                sourceRoot,
                targetRoot,
                VerificationMode.SIZE_AND_MTIME,
                record,
                (bytesDelta, absoluteTransferred) -> {
                }
        );

        assertFalse(Files.exists(firstFile));
        assertTrue(Files.exists(secondFile));
        assertTrue(Files.exists(nestedDir));
        assertTrue(Files.exists(sourceRoot.resolve("nested")));
    }

    @Test
    void shouldDeleteSourceFileWhenReusingExistingTarget() throws Exception {
        TransferProperties properties = buildProperties();
        ScalableFileCopyService service = new ScalableFileCopyService(
                properties,
                new FileIntegrityService(properties)
        );

        Path sourceRoot = tempDir.resolve("source-reuse");
        Path targetRoot = tempDir.resolve("target-reuse");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(targetRoot);
        Path sourceFile = sourceRoot.resolve("reused.txt");
        Path targetFile = targetRoot.resolve("reused.txt");
        Files.writeString(sourceFile, "already-there");
        Files.writeString(targetFile, "already-there");

        ScalableFileRecord record = buildRecord("reused.txt", sourceFile);

        service.copyFile(
                sourceRoot,
                targetRoot,
                VerificationMode.FULL_HASH,
                record,
                (bytesDelta, absoluteTransferred) -> {
                }
        );

        assertFalse(Files.exists(sourceFile));
        assertTrue(Files.exists(targetFile));
        assertEquals("already-there", Files.readString(targetFile));
    }

    private TransferProperties buildProperties() {
        TransferProperties properties = new TransferProperties();
        properties.setBufferSize(1024);
        properties.setProgressSaveIntervalBytes(1);
        properties.setHashAlgorithm("SHA-256");
        return properties;
    }

    private ScalableFileRecord buildRecord(String relativePath, Path sourceFile) throws Exception {
        return ScalableFileRecord.builder()
                .id(1L)
                .taskId("task-1")
                .batchId(1L)
                .relativePath(relativePath)
                .sourceSize(Files.size(sourceFile))
                .transferredBytes(0L)
                .sourceLastModifiedMillis(Files.getLastModifiedTime(sourceFile).toMillis())
                .status(FileTransferStatus.PENDING)
                .build();
    }
}
