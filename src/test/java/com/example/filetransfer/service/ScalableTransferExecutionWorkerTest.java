package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.domain.BatchStatus;
import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.FileTransferStatus;
import com.example.filetransfer.domain.ScalableFileRecord;
import com.example.filetransfer.domain.TransferBatch;
import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.TransferTask;
import com.example.filetransfer.domain.TransferType;
import com.example.filetransfer.domain.VerificationMode;
import com.example.filetransfer.exception.TransferException;
import com.example.filetransfer.mapper.ScalableFileRecordMapper;
import com.example.filetransfer.mapper.TransferBatchMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScalableTransferExecutionWorkerTest {

    @Test
    void shouldContinueProcessingNextFileWhenOneFileFails() {
        TransferProperties transferProperties = new TransferProperties();
        transferProperties.setScalableExecutionFilePageSize(10);
        transferProperties.setScalableExecutionBatchPageSize(1);
        transferProperties.setMaxRetries(0);

        StatePersistenceService statePersistenceService = mock(StatePersistenceService.class);
        ScalableExecutionStateService executionStateService = mock(ScalableExecutionStateService.class);
        ScalableProgressService scalableProgressService = mock(ScalableProgressService.class);
        ScalableFileCopyService scalableFileCopyService = mock(ScalableFileCopyService.class);
        ScalableTaskControlService scalableTaskControlService = mock(ScalableTaskControlService.class);
        ScalableRuntimeMonitorService scalableRuntimeMonitorService = mock(ScalableRuntimeMonitorService.class);
        ScalableSchedulingService scalableSchedulingService = mock(ScalableSchedulingService.class);
        DispatchQueueStore dispatchQueueStore = mock(DispatchQueueStore.class);
        TransferBatchMapper transferBatchMapper = mock(TransferBatchMapper.class);
        ScalableFileRecordMapper scalableFileRecordMapper = mock(ScalableFileRecordMapper.class);
        TransferExecutionRecordService transferExecutionRecordService = mock(TransferExecutionRecordService.class);
        ThreadPoolTaskExecutor transferExecutor = new ThreadPoolTaskExecutor();
        transferExecutor.initialize();

        ScalableTransferExecutionWorker worker = new ScalableTransferExecutionWorker(
                transferProperties,
                statePersistenceService,
                executionStateService,
                scalableProgressService,
                scalableFileCopyService,
                scalableTaskControlService,
                scalableRuntimeMonitorService,
                scalableSchedulingService,
                dispatchQueueStore,
                transferBatchMapper,
                scalableFileRecordMapper,
                transferExecutionRecordService,
                transferExecutor
        );

        TransferTask task = TransferTask.builder()
                .id("task-1")
                .sourcePath("C:/source")
                .targetPath("C:/target")
                .transferType(TransferType.DIRECTORY)
                .status(TransferStatus.RUNNING)
                .verificationMode(VerificationMode.SIZE_AND_MTIME)
                .build();
        TransferBatch batch = TransferBatch.builder()
                .id(10L)
                .taskId(task.getId())
                .batchNumber(1)
                .status(BatchStatus.SCANNED)
                .temperatureTier(BatchTemperature.HOT)
                .schedulingPriority(1)
                .fileCount(2)
                .totalBytes(20L)
                .transferredBytes(0L)
                .build();
        ScalableFileRecord first = record(1L, task.getId(), batch.getId(), "a.txt");
        ScalableFileRecord second = record(2L, task.getId(), batch.getId(), "b.txt");

        when(scalableTaskControlService.isCancelRequested(task.getId())).thenReturn(false);
        when(scalableTaskControlService.isPauseRequested(task.getId())).thenReturn(false);
        when(statePersistenceService.getTask(task.getId())).thenReturn(task);
        when(transferBatchMapper.selectNextBatches(anyString(), anyList(), anyInt(), anyInt())).thenReturn(List.of(batch), List.of());
        when(transferBatchMapper.selectByIds(anyList())).thenReturn(List.of(batch));
        when(scalableFileRecordMapper.selectList(any())).thenReturn(List.of(first, second), List.of());
        Map<com.example.filetransfer.domain.ThrottleReason, Boolean> reasons =
                new java.util.EnumMap<>(com.example.filetransfer.domain.ThrottleReason.class);
        for (com.example.filetransfer.domain.ThrottleReason reason : com.example.filetransfer.domain.ThrottleReason.values()) {
            reasons.put(reason, false);
        }
        when(scalableSchedulingService.evaluateDispatch(anyString(), any(), any(), any())).thenReturn(
                new ScalableSchedulingService.DispatchDecision(false, reasons)
        );
        when(scalableFileCopyService.copyFile(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            ScalableFileRecord record = invocation.getArgument(3);
            if (record.getId().equals(first.getId())) {
                throw new TransferException("boom");
            }
            return new ScalableFileCopyService.CopyResult(record.getSourceSize(), "hash-" + record.getId());
        });

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(worker, "processBatch",
                task.getId(),
                task,
                Path.of("C:/source"),
                Path.of("C:/target"),
                batch,
                new AtomicBoolean(false)));

        verify(scalableFileCopyService, times(2)).copyFile(any(), any(), any(), any(), any());
        verify(scalableProgressService).increment(task.getId(), 0L, 1);
        verify(executionStateService).updateFileStatus(first.getId(), task.getId(), FileTransferStatus.FAILED, 0L, "File copy exhausted retries: a.txt", null);
        verify(executionStateService).updateFileStatus(second.getId(), task.getId(), FileTransferStatus.COMPLETED, second.getSourceSize(), null, "hash-" + second.getId());
        verify(executionStateService).updateBatchStatus(batch.getId(), BatchStatus.RUNNING, 0L, "File copy exhausted retries: a.txt");
        verify(executionStateService, never()).updateBatchStatus(batch.getId(), BatchStatus.FAILED, 0L, "File copy exhausted retries: a.txt");
    }

    private ScalableFileRecord record(Long id, String taskId, Long batchId, String relativePath) {
        return ScalableFileRecord.builder()
                .id(id)
                .taskId(taskId)
                .batchId(batchId)
                .relativePath(relativePath)
                .sourceSize(10L)
                .transferredBytes(0L)
                .sourceLastModifiedMillis(1L)
                .status(FileTransferStatus.PENDING)
                .build();
    }
}
