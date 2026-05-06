package com.example.filetransfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.filetransfer.domain.TransferBatch;
import com.example.filetransfer.mapper.TransferBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScalableBatchRepositoryAdapter {

    private final TransferBatchMapper transferBatchMapper;

    public org.springframework.data.domain.Page<TransferBatch> findBatches(String taskId, int page, int size) {
        Page<TransferBatch> request = new Page<>(page + 1L, size);
        Page<TransferBatch> result = transferBatchMapper.selectPage(
                request,
                new LambdaQueryWrapper<TransferBatch>()
                        .eq(TransferBatch::getTaskId, taskId)
                        .orderByAsc(TransferBatch::getBatchNumber)
        );
        return new PageImpl<>(result.getRecords(), PageRequest.of(page, size), result.getTotal());
    }
}
