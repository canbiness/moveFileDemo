package com.example.filetransfer.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.filetransfer.domain.DataTransferJob;
import com.example.filetransfer.domain.DataTransferRule;
import com.example.filetransfer.domain.VerificationMode;
import com.example.filetransfer.dto.DataTransferJobPlanResponse;
import com.example.filetransfer.dto.ScalableTransferPlanResponse;
import com.example.filetransfer.exception.TransferException;
import com.example.filetransfer.mapper.DataTransferJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DataTransferJobServiceImpl extends ServiceImpl<DataTransferJobMapper, DataTransferJob> implements DataTransferJobService {

    private final DataTransferRuleService dataTransferRuleService;
    private final ScalableTransferPlannerService scalableTransferPlannerService;

    @Override
    public DataTransferJob requireById(Long jobId) {
        DataTransferJob job = getById(jobId);
        if (job == null) {
            throw new TransferException("DataTransferJob not found: " + jobId);
        }
        return job;
    }

    @Override
    @Transactional
    public DataTransferJobPlanResponse createPlanByJobId(Long jobId) {
        DataTransferJob job = requireById(jobId);
        DataTransferRule rule = dataTransferRuleService.getById(job.getRuleId());
        if (rule == null) {
            throw new TransferException("DataTransferRule not found: " + job.getRuleId());
        }
        ScalableTransferPlanResponse plan = scalableTransferPlannerService.createPlan(
                rule.getPath(),
                rule.getTargetPath(),
                VerificationMode.SIZE_AND_MTIME
        );
        String taskId = plan.taskId() != null ? plan.taskId() : UUID.randomUUID().toString();
        job.setTaskId(taskId);
        updateById(job);
        return new DataTransferJobPlanResponse(
                job.getId(),
                job.getRuleId(),
                rule.getPath(),
                rule.getTargetPath(),
                taskId,
                plan.status(),
                plan.recommendation()
        );
    }
}
