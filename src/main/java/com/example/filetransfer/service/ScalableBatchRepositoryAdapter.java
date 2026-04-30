package com.example.filetransfer.service;

import com.example.filetransfer.domain.TransferBatch;
import com.example.filetransfer.repository.TransferBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 批次仓储适配器。
 * 隔离分页参数构造，便于后续替换游标式分页或更复杂的查询策略。
 */
@Component
@RequiredArgsConstructor
public class ScalableBatchRepositoryAdapter {

    private final TransferBatchRepository transferBatchRepository;

    public Page<TransferBatch> findBatches(String taskId, int page, int size) {
        return transferBatchRepository.findByTaskIdOrderByBatchNumberAsc(taskId, PageRequest.of(page, size));
    }
}
