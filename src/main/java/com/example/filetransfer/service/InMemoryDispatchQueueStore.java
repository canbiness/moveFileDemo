package com.example.filetransfer.service;

import com.example.filetransfer.domain.BatchTemperature;
import com.example.filetransfer.domain.TransferBatch;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的调度热队列实现。
 * 作为 Redis 未启用时的默认回退实现。
 */
@Service
@ConditionalOnProperty(prefix = "transfer", name = "redis-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryDispatchQueueStore implements DispatchQueueStore {

    private final ConcurrentHashMap<String, Map<BatchTemperature, PriorityQueue<QueueEntry>>> taskQueues = new ConcurrentHashMap<>();

    @Override
    public void clearTask(String taskId) {
        taskQueues.remove(taskId);
    }

    @Override
    public void enqueueBatch(String taskId, TransferBatch batch) {
        taskQueues.computeIfAbsent(taskId, ignored -> createQueues())
                .get(batch.getTemperatureTier())
                .offer(new QueueEntry(batch.getId(), batch.getSchedulingPriority(), batch.getBatchNumber()));
    }

    @Override
    public List<Long> pollBatchIds(String taskId, int limit, int hotDispatch, int warmDispatch, int coldDispatch) {
        Map<BatchTemperature, PriorityQueue<QueueEntry>> queues = taskQueues.get(taskId);
        if (queues == null) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(limit);
        while (ids.size() < limit && hasRemaining(queues)) {
            drainQueue(queues.get(BatchTemperature.HOT), ids, hotDispatch, limit);
            drainQueue(queues.get(BatchTemperature.WARM), ids, warmDispatch, limit);
            drainQueue(queues.get(BatchTemperature.COLD), ids, coldDispatch, limit);
        }
        return ids;
    }

    private Map<BatchTemperature, PriorityQueue<QueueEntry>> createQueues() {
        Comparator<QueueEntry> comparator = Comparator
                .comparingInt(QueueEntry::schedulingPriority)
                .thenComparingInt(QueueEntry::batchNumber);
        EnumMap<BatchTemperature, PriorityQueue<QueueEntry>> queues = new EnumMap<>(BatchTemperature.class);
        for (BatchTemperature temperature : BatchTemperature.values()) {
            queues.put(temperature, new PriorityQueue<>(comparator));
        }
        return queues;
    }

    private boolean hasRemaining(Map<BatchTemperature, PriorityQueue<QueueEntry>> queues) {
        return queues.values().stream().anyMatch(queue -> !queue.isEmpty());
    }

    private void drainQueue(PriorityQueue<QueueEntry> queue, List<Long> ids, int count, int limit) {
        for (int i = 0; i < count && ids.size() < limit && !queue.isEmpty(); i++) {
            ids.add(queue.poll().batchId());
        }
    }

    private record QueueEntry(Long batchId, int schedulingPriority, int batchNumber) {
    }
}
