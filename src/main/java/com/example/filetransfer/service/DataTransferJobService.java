package com.example.filetransfer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.filetransfer.domain.DataTransferJob;
import com.example.filetransfer.dto.DataTransferJobPlanResponse;

public interface DataTransferJobService extends IService<DataTransferJob> {

    DataTransferJob requireById(Long jobId);

    DataTransferJobPlanResponse createPlanByJobId(Long jobId);
}
