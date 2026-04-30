package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.ThrottleReason;
import com.example.filetransfer.domain.TransferBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

/**
 * 超大规模任务调度策略服务。
 * 负责冷热分层、加权公平调度与背压判断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScalableSchedulingService {

    private final TransferProperties transferProperties;

    public List<TransferBatch> prioritizeCandidates(List<TransferBatch> candidates) {
        Queue<TransferBatch> hotQueue = buildTemperatureQueue(candidates, BatchTemperature.HOT);
        Queue<TransferBatch> warmQueue = buildTemperatureQueue(candidates, BatchTemperature.WARM);
        Queue<TransferBatch> coldQueue = buildTemperatureQueue(candidates, BatchTemperature.COLD);
        log.debug("开始对候选批次进行冷热分层公平排序, totalCandidates={}, hotCount={}, warmCount={}, coldCount={}",
                candidates.size(), hotQueue.size(), warmQueue.size(), coldQueue.size());

        List<TransferBatch> prioritized = new ArrayList<>(candidates.size());
        while (!hotQueue.isEmpty() || !warmQueue.isEmpty() || !coldQueue.isEmpty()) {
            drainByBurst(hotQueue, prioritized, transferProperties.getHotDispatchBurst());
            drainByBurst(warmQueue, prioritized, transferProperties.getWarmDispatchBurst());
            drainByBurst(coldQueue, prioritized, transferProperties.getColdDispatchBurst());
        }
        return prioritized;
    }

    public DispatchDecision evaluateDispatch(String taskId,
                                             TransferBatch batch,
                                             ThreadPoolTaskExecutor transferExecutor,
                                             ScalableRuntimeMonitorService.RuntimeSnapshot runtimeSnapshot) {
        int maxPoolSize = Math.max(1, transferExecutor.getMaxPoolSize());
        int queueCapacity = transferExecutor.getThreadPoolExecutor().getQueue().size()
                + transferExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
        int queueSize = transferExecutor.getThreadPoolExecutor().getQueue().size();

        boolean threadPoolBusy = transferExecutor.getActiveCount() * 100 >= maxPoolSize * transferProperties.getBackpressureHighWatermarkPercent();
        boolean queueBusy = queueCapacity > 0
                && queueSize * 100 >= queueCapacity * transferProperties.getBackpressureHighWatermarkPercent();
        boolean bytesBusy = runtimeSnapshot.inFlightBytes() + batch.getTotalBytes() > transferProperties.getMaxInFlightBytes();
        boolean coldBusy = batch.getTemperatureTier() == BatchTemperature.COLD
                && runtimeSnapshot.inFlightTemperatureCounts().getOrDefault(BatchTemperature.COLD, 0L)
                >= transferProperties.getColdBatchConcurrencyLimit();

        EnumMap<ThrottleReason, Boolean> reasons = new EnumMap<>(ThrottleReason.class);
        reasons.put(ThrottleReason.THREAD_POOL, threadPoolBusy);
        reasons.put(ThrottleReason.QUEUE, queueBusy);
        reasons.put(ThrottleReason.IN_FLIGHT_BYTES, bytesBusy);
        reasons.put(ThrottleReason.COLD_LIMIT, coldBusy);

        boolean throttled = threadPoolBusy || queueBusy || bytesBusy || coldBusy;
        if (throttled) {
            log.debug("触发调度背压, taskId={}, batchId={}, batchNumber={}, threadPoolBusy={}, queueBusy={}, bytesBusy={}, coldBusy={}, activeCount={}, queueSize={}, inFlightBytes={}",
                    taskId, batch.getId(), batch.getBatchNumber(), threadPoolBusy, queueBusy, bytesBusy, coldBusy,
                    transferExecutor.getActiveCount(), queueSize, runtimeSnapshot.inFlightBytes());
        }
        return new DispatchDecision(throttled, reasons);
    }

    public BatchTemperature classifyTemperature(long totalBytes, int fileCount) {
        if (totalBytes <= transferProperties.getHotBatchBytesThreshold()
                && fileCount <= transferProperties.getHotBatchFileCountThreshold()) {
            return BatchTemperature.HOT;
        }
        if (totalBytes <= transferProperties.getWarmBatchBytesThreshold()) {
            return BatchTemperature.WARM;
        }
        return BatchTemperature.COLD;
    }

    public int calculateSchedulingPriority(BatchTemperature temperature, long totalBytes, int fileCount, int batchNumber) {
        int base = switch (temperature) {
            case HOT -> 100;
            case WARM -> 200;
            case COLD -> 300;
        };
        int byteWeight = (int) Math.min(totalBytes / (64L * 1024 * 1024), 99);
        int fileWeight = Math.min(fileCount / 100, 99);
        return base + byteWeight + fileWeight + Math.min(batchNumber / 10, 99);
    }

    private Queue<TransferBatch> buildTemperatureQueue(List<TransferBatch> candidates, BatchTemperature temperature) {
        return candidates.stream()
                .filter(batch -> batch.getTemperatureTier() == temperature)
                .sorted(Comparator
                        .comparing(TransferBatch::getSchedulingPriority)
                        .thenComparing(TransferBatch::getTotalBytes)
                        .thenComparing(TransferBatch::getBatchNumber))
                .collect(ArrayDeque::new, Queue::add, Queue::addAll);
    }

    private void drainByBurst(Queue<TransferBatch> queue, List<TransferBatch> result, int burst) {
        for (int i = 0; i < burst && !queue.isEmpty(); i++) {
            result.add(Objects.requireNonNull(queue.poll()));
        }
    }

    /**
     * 派发决策结果。
     *
     * @param throttled 是否需要背压
     * @param reasons   各类背压原因是否触发
     */
    public record DispatchDecision(boolean throttled, Map<ThrottleReason, Boolean> reasons) {
    }
}
