package com.example.filetransfer.service;

import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.VerificationMode;
import com.example.filetransfer.dto.ScalableTransferBatchPageResponse;
import com.example.filetransfer.dto.ScalableTransferPlanResponse;
import com.example.filetransfer.dto.ScalableTransferTaskSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ScalableTransferPlannerServiceTest {

    @Autowired
    private ScalableTransferPlannerService scalableTransferPlannerService;

    @Autowired
    private ScalableTransferQueryService scalableTransferQueryService;

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateScalablePlanForDirectory() throws Exception {
        Path source = tempDir.resolve("source");
        Path target = tempDir.resolve("target");
        Files.createDirectories(source.resolve("nested"));
        Files.createDirectories(target);
        Files.writeString(source.resolve("a.txt"), "alpha");
        Files.writeString(source.resolve("nested").resolve("b.txt"), "beta");

        ScalableTransferPlanResponse response = scalableTransferPlannerService.createPlan(
                source.toString(),
                target.toString(),
                VerificationMode.SIZE_AND_MTIME
        );

        assertNotNull(response.taskId());
        assertEquals(TransferStatus.PLANNING, response.status());
        assertEquals(0, response.totalFiles());
        assertEquals(0, response.totalBytes());
        assertEquals(0, response.batchCount());
        assertEquals(VerificationMode.SIZE_AND_MTIME, response.verificationMode());

        ScalableTransferTaskSummaryResponse planningSummary = scalableTransferQueryService.getSummary(response.taskId());
        assertTrue(planningSummary.status() == TransferStatus.PLANNING
                || planningSummary.status() == TransferStatus.PLANNED);

        ScalableTransferTaskSummaryResponse summary = waitForPlanned(response.taskId());
        assertEquals(TransferStatus.PLANNED, summary.status());
        assertEquals(2, summary.totalFiles());
        assertTrue(summary.totalBytes() > 0);
        assertEquals(0.0D, summary.progressPercent());

        ScalableTransferBatchPageResponse batches = scalableTransferQueryService.getBatches(response.taskId(), 0, 10);
        assertEquals(1, batches.totalElements());
        assertEquals(1, batches.batches().size());
    }

    private ScalableTransferTaskSummaryResponse waitForPlanned(String taskId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        ScalableTransferTaskSummaryResponse summary = null;
        while (Instant.now().isBefore(deadline)) {
            summary = scalableTransferQueryService.getSummary(taskId);
            if (summary.status() == TransferStatus.PLANNED || summary.status() == TransferStatus.FAILED) {
                return summary;
            }
            Thread.sleep(100L);
        }
        return summary;
    }
}
