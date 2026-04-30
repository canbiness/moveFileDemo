package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.ThrottleReason;
import com.example.filetransfer.domain.TransferBatch;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证超大规模任务调度策略的冷热分层、公平出队和背压判定行为。
 */
class ScalableSchedulingServiceTest {

    @Test
    void shouldInterleaveBatchesByTemperatureBurstPolicy() {
        TransferProperties properties = buildProperties();
        ScalableSchedulingService service = new ScalableSchedulingService(properties);

        List<TransferBatch> prioritized = service.prioritizeCandidates(List.of(
                batch(1L, 1, BatchTemperature.COLD, 310, 8L * 1024 * 1024 * 1024, 10_000),
                batch(2L, 2, BatchTemperature.HOT, 101, 16L * 1024 * 1024, 50),
                batch(3L, 3, BatchTemperature.HOT, 102, 24L * 1024 * 1024, 80),
                batch(4L, 4, BatchTemperature.HOT, 103, 32L * 1024 * 1024, 100),
                batch(5L, 5, BatchTemperature.HOT, 104, 40L * 1024 * 1024, 120),
                batch(6L, 6, BatchTemperature.WARM, 205, 2L * 1024 * 1024 * 1024, 1_000),
                batch(7L, 7, BatchTemperature.WARM, 206, 3L * 1024 * 1024 * 1024, 1_500)
        ));

        assertEquals(List.of(2L, 3L, 4L, 5L, 6L, 7L, 1L),
                prioritized.stream().map(TransferBatch::getId).toList());
    }

    @Test
    void shouldExposeThrottleReasonsWhenDispatchIsLimited() {
        TransferProperties properties = buildProperties();
        properties.setBackpressureHighWatermarkPercent(50);
        properties.setMaxInFlightBytes(1024L);
        properties.setColdBatchConcurrencyLimit(1);
        ScalableSchedulingService service = new ScalableSchedulingService(properties);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();
        try {
            executor.getThreadPoolExecutor().submit(() -> sleepSilently(500L));
            executor.getThreadPoolExecutor().submit(() -> sleepSilently(500L));
            sleepSilently(50L);

            ScalableRuntimeMonitorService.RuntimeSnapshot snapshot =
                    new ScalableRuntimeMonitorService.RuntimeSnapshot(
                            "temperature-priority-backpressure",
                            true,
                            1L,
                            1L,
                            800L,
                            0L,
                            0L,
                            java.util.Map.of(
                                    BatchTemperature.HOT, 0L,
                                    BatchTemperature.WARM, 0L,
                                    BatchTemperature.COLD, 1L
                            ),
                            java.util.Map.of(
                                    ThrottleReason.THREAD_POOL, 0L,
                                    ThrottleReason.QUEUE, 0L,
                                    ThrottleReason.IN_FLIGHT_BYTES, 0L,
                                    ThrottleReason.COLD_LIMIT, 0L
                            )
                    );

            var decision = service.evaluateDispatch(
                    "task-1",
                    batch(8L, 8, BatchTemperature.COLD, 308, 600L, 10),
                    executor,
                    snapshot
            );

            assertTrue(decision.throttled());
            assertTrue(decision.reasons().get(ThrottleReason.THREAD_POOL));
            assertTrue(decision.reasons().get(ThrottleReason.QUEUE));
            assertTrue(decision.reasons().get(ThrottleReason.IN_FLIGHT_BYTES));
            assertTrue(decision.reasons().get(ThrottleReason.COLD_LIMIT));
        } finally {
            executor.shutdown();
        }
    }

    private TransferProperties buildProperties() {
        TransferProperties properties = new TransferProperties();
        properties.setHotDispatchBurst(4);
        properties.setWarmDispatchBurst(2);
        properties.setColdDispatchBurst(1);
        properties.setBackpressureHighWatermarkPercent(85);
        properties.setMaxInFlightBytes(32L * 1024 * 1024 * 1024);
        properties.setColdBatchConcurrencyLimit(1);
        properties.setHotBatchBytesThreshold(512L * 1024 * 1024);
        properties.setWarmBatchBytesThreshold(4L * 1024 * 1024 * 1024);
        properties.setHotBatchFileCountThreshold(2_000);
        return properties;
    }

    private TransferBatch batch(Long id,
                                int batchNumber,
                                BatchTemperature temperature,
                                int schedulingPriority,
                                long totalBytes,
                                int fileCount) {
        return TransferBatch.builder()
                .id(id)
                .taskId("task-1")
                .batchNumber(batchNumber)
                .temperatureTier(temperature)
                .schedulingPriority(schedulingPriority)
                .fileCount(fileCount)
                .totalBytes(totalBytes)
                .transferredBytes(0L)
                .build();
    }

    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
