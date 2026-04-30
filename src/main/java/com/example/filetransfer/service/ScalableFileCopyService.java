package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.domain.ScalableFileRecord;
import com.example.filetransfer.domain.VerificationMode;
import com.example.filetransfer.exception.TransferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;

/**
 * 单文件复制服务。
 * 负责处理断点续传、`.part` 临时文件、复制后校验和最终 promote。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScalableFileCopyService {

    @FunctionalInterface
    public interface CopyProgressListener {
        /**
         * 上报本次增加的字节数以及当前绝对传输位置。
         */
        void onProgress(long bytesDelta, long absoluteTransferred);
    }

    /**
     * 单文件复制完成后的结果。
     */
    public record CopyResult(long transferredBytes, String targetHash) {
    }

    /** 全局迁移配置。 */
    private final TransferProperties transferProperties;
    /** 文件校验服务。 */
    private final FileIntegrityService fileIntegrityService;
    /** 线程级直接内存缓冲区缓存，避免每次复制都新建 ByteBuffer。 */
    private final ThreadLocal<ByteBuffer> directBufferCache = new ThreadLocal<>();

    /**
     * 复制单个文件。
     *
     * @param sourceRoot 源根目录
     * @param targetRoot 目标根目录
     * @param verificationMode 校验模式
     * @param record 文件记录
     * @param progressListener 进度回调
     * @return 复制结果
     */
    public CopyResult copyFile(Path sourceRoot,
                               Path targetRoot,
                               VerificationMode verificationMode,
                               ScalableFileRecord record,
                               CopyProgressListener progressListener) {
        Path sourceFile = sourceRoot.resolve(record.getRelativePath()).normalize();
        Path targetFile = targetRoot.resolve(record.getRelativePath()).normalize();
        Path partFile = resolvePartFile(targetFile);
        log.debug("Starting file copy, relativePath={}, sourceFile={}, targetFile={}, verificationMode={}, persistedOffset={}",
                record.getRelativePath(), sourceFile, targetFile, verificationMode, record.getTransferredBytes());

        try {
            if (targetFile.getParent() != null) {
                // 目标父目录不存在时先创建，保证后续 .part 文件可写。
                Files.createDirectories(targetFile.getParent());
            }
            long sourceSize = record.getSourceSize();
            long sourceLastModifiedMillis = record.getSourceLastModifiedMillis();
            // 持久化偏移量最小也只能从 0 开始。
            long persistedOffset = Math.max(0L, record.getTransferredBytes());

            // 如果目标文件本身已经存在且校验通过，则无需重复复制。
            ExistingTargetState existingTargetState = inspectExistingTarget(
                    sourceFile,
                    targetFile,
                    verificationMode,
                    sourceSize,
                    sourceLastModifiedMillis
            );
            if (existingTargetState.valid()) {
                Files.deleteIfExists(partFile);
                // 如果数据库进度还没追到终点，这里补发一笔进度，让上层状态对齐到完成态。
                long delta = Math.max(0L, sourceSize - persistedOffset);
                if (delta > 0) {
                    progressListener.onProgress(delta, sourceSize);
                }
                log.debug("Reused verified target file, relativePath={}, sourceSize={}, targetHash={}",
                        record.getRelativePath(), sourceSize, existingTargetState.targetHash());
                return new CopyResult(sourceSize, existingTargetState.targetHash());
            }

            // 优先以 .part 文件的真实长度作为可恢复偏移。
            long resumeOffset = Files.exists(partFile) ? Files.size(partFile) : 0L;
            if (resumeOffset > sourceSize) {
                // .part 文件比源文件还大，说明临时文件已经异常，直接丢弃重来。
                log.warn("Discarding oversized part file, relativePath={}, resumeOffset={}, sourceSize={}",
                        record.getRelativePath(), resumeOffset, sourceSize);
                Files.deleteIfExists(partFile);
                resumeOffset = 0L;
            }

            long absoluteOffset = alignResumeOffset(record, resumeOffset, persistedOffset);
            if (absoluteOffset != persistedOffset) {
                // 当真实续传位置和数据库偏移不一致时，要把差值同步给上层进度。
                progressListener.onProgress(absoluteOffset - persistedOffset, absoluteOffset);
                log.debug("Adjusted persisted progress to match part file, relativePath={}, persistedOffset={}, absoluteOffset={}",
                        record.getRelativePath(), persistedOffset, absoluteOffset);
            }

            if (absoluteOffset >= sourceSize) {
                // 已经复制到文件尾时，只需要做校验和 promote。
                String targetHash = verifyAndPromote(
                        sourceFile,
                        partFile,
                        targetFile,
                        verificationMode,
                        sourceSize,
                        sourceLastModifiedMillis
                );
                return new CopyResult(sourceSize, targetHash);
            }

            if (sourceSize == 0L) {
                // 空文件不需要走复制循环，直接创建空的 .part 并走后续校验/提升流程。
                Files.deleteIfExists(partFile);
                Files.createFile(partFile);
                String targetHash = verifyAndPromote(
                        sourceFile,
                        partFile,
                        targetFile,
                        verificationMode,
                        sourceSize,
                        sourceLastModifiedMillis
                );
                return new CopyResult(0L, targetHash);
            }

            // 从续传偏移开始继续复制剩余内容。
            long copiedBytes = copyRemainingBytes(
                    sourceFile,
                    partFile,
                    sourceSize,
                    absoluteOffset,
                    absoluteOffset,
                    progressListener
            );
            String targetHash = verifyAndPromote(
                    sourceFile,
                    partFile,
                    targetFile,
                    verificationMode,
                    sourceSize,
                    sourceLastModifiedMillis
            );
            log.debug("Completed file copy and promote, relativePath={}, copiedBytes={}, finalBytes={}",
                    record.getRelativePath(), copiedBytes, copiedBytes + absoluteOffset);
            return new CopyResult(copiedBytes + absoluteOffset, targetHash);
        } catch (IOException ex) {
            throw new TransferException("Failed to copy file: " + sourceFile, ex);
        }
    }

    /**
     * 从指定偏移开始复制剩余字节。
     */
    private long copyRemainingBytes(Path sourceFile,
                                    Path partFile,
                                    long sourceSize,
                                    long startOffset,
                                    long persistedOffset,
                                    CopyProgressListener progressListener) throws IOException {
        long copiedBytes = 0L;
        long reportedOffset = persistedOffset;
        ByteBuffer buffer = acquireBuffer();

        try (FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ);
             FileChannel targetChannel = FileChannel.open(partFile,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE)) {
            long position = startOffset;
            while (position < sourceSize) {
                // 先把源文件当前位置的数据读到直接缓冲区。
                buffer.clear();
                int read = sourceChannel.read(buffer, position);
                if (read <= 0) {
                    throw new IOException("Failed to read source file: " + sourceFile);
                }
                buffer.flip();
                int written = 0;
                while (buffer.hasRemaining()) {
                    // FileChannel 可能一次写不完，所以这里循环直到当前缓冲区全部落盘。
                    int bytesWritten = targetChannel.write(buffer, position + written);
                    if (bytesWritten <= 0) {
                        throw new IOException("Failed to write to temporary file: " + partFile);
                    }
                    written += bytesWritten;
                }
                position += written;
                copiedBytes += written;
                // 复制达到进度持久化粒度后，再统一向上层上报一次，减少频繁刷库。
                if (position - reportedOffset >= transferProperties.getProgressSaveIntervalBytes()) {
                    long delta = position - reportedOffset;
                    progressListener.onProgress(delta, position);
                    reportedOffset = position;
                }
            }
            if (position > reportedOffset) {
                // 循环结束后，如果最后一段还没上报，也要补发一次最终进度。
                progressListener.onProgress(position - reportedOffset, position);
            }
        }

        return copiedBytes;
    }

    /**
     * 获取线程级直接缓冲区，减少大文件复制时的对象分配开销。
     */
    private ByteBuffer acquireBuffer() {
        int bufferSize = transferProperties.getBufferSize();
        ByteBuffer buffer = directBufferCache.get();
        if (buffer == null || buffer.capacity() != bufferSize) {
            buffer = ByteBuffer.allocateDirect(bufferSize);
            directBufferCache.set(buffer);
        }
        buffer.clear();
        return buffer;
    }

    /**
     * 检查已存在的目标文件是否可以直接复用。
     */
    private ExistingTargetState inspectExistingTarget(Path sourceFile,
                                                      Path targetFile,
                                                      VerificationMode verificationMode,
                                                      long sourceSize,
                                                      long sourceLastModifiedMillis) {
        if (!Files.exists(targetFile)) {
            return ExistingTargetState.notValid();
        }

        try {
            // 目标文件大小不同，直接视为不可复用。
            if (Files.size(targetFile) != sourceSize) {
                return ExistingTargetState.notValid();
            }
            // 在 SIZE_AND_MTIME 模式下，大小一致还不够，修改时间也必须一致。
            if (verificationMode == VerificationMode.SIZE_AND_MTIME
                    && Files.getLastModifiedTime(targetFile).toMillis() != sourceLastModifiedMillis) {
                return ExistingTargetState.notValid();
            }
            if (verificationMode == VerificationMode.FULL_HASH) {
                // FULL_HASH 模式下必须比较源文件和目标文件哈希。
                String sourceHash = fileIntegrityService.calculateHash(sourceFile, transferProperties.getHashAlgorithm());
                String targetHash = fileIntegrityService.calculateHash(targetFile, transferProperties.getHashAlgorithm());
                if (!sourceHash.equals(targetHash)) {
                    return ExistingTargetState.notValid();
                }
                return new ExistingTargetState(true, targetHash);
            }
            return new ExistingTargetState(true, null);
        } catch (IOException ex) {
            throw new TransferException("Failed to inspect existing target file: " + targetFile, ex);
        }
    }

    /**
     * 校验临时文件并提升为最终目标文件。
     */
    private String verifyAndPromote(Path sourceFile,
                                    Path partFile,
                                    Path targetFile,
                                    VerificationMode verificationMode,
                                    long sourceSize,
                                    long sourceLastModifiedMillis) throws IOException {
        if (!Files.exists(partFile)) {
            throw new TransferException("Temporary file missing: " + partFile);
        }
        if (verificationMode == VerificationMode.FULL_HASH) {
            // 最严格模式下，对比源文件与临时文件哈希。
            String sourceHash = fileIntegrityService.calculateHash(sourceFile, transferProperties.getHashAlgorithm());
            String targetHash = fileIntegrityService.calculateHash(partFile, transferProperties.getHashAlgorithm());
            if (!sourceHash.equals(targetHash)) {
                throw new TransferException("Hash mismatch detected for file: " + sourceFile);
            }
            promote(partFile, targetFile, sourceLastModifiedMillis);
            return targetHash;
        }

        // 非哈希模式下至少要保证最终文件大小正确。
        if (Files.size(partFile) != sourceSize) {
            throw new TransferException("File size mismatch detected for file: " + sourceFile);
        }
        promote(partFile, targetFile, sourceLastModifiedMillis);
        if (verificationMode == VerificationMode.SIZE_AND_MTIME
                // promote 之后还要再次确认目标文件的修改时间已经恢复正确。
                && Files.getLastModifiedTime(targetFile).toMillis() != sourceLastModifiedMillis) {
            throw new TransferException("File mtime mismatch detected for file: " + sourceFile);
        }
        return null;
    }

    /**
     * 将 `.part` 文件原子替换到最终目标路径，并恢复源文件修改时间。
     */
    private void promote(Path partFile, Path targetFile, long sourceLastModifiedMillis) throws IOException {
        Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(targetFile, FileTime.fromMillis(sourceLastModifiedMillis));
    }

    private Path resolvePartFile(Path targetFile) {
        // 目标文件的临时续传文件统一采用同级目录下追加 .part 后缀的命名方式。
        String fileName = targetFile.getFileName().toString() + ".part";
        return targetFile.resolveSibling(fileName);
    }

    /**
     * 对齐续传偏移量。
     * 当数据库偏移和 .part 实际长度不一致时，以 .part 真实长度为准。
     */
    private long alignResumeOffset(ScalableFileRecord record, long resumeOffset, long persistedOffset) {
        if (resumeOffset < persistedOffset) {
            log.warn("Part file is behind persisted progress, resuming from actual part size, relativePath={}, persistedOffset={}, resumeOffset={}",
                    record.getRelativePath(), persistedOffset, resumeOffset);
            return resumeOffset;
        }
        return resumeOffset;
    }

    /**
     * 目标文件复用检查结果。
     */
    private record ExistingTargetState(boolean valid, String targetHash) {
        private static ExistingTargetState notValid() {
            return new ExistingTargetState(false, null);
        }
    }
}
