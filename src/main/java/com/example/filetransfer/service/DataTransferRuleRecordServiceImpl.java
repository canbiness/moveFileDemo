package com.example.filetransfer.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.filetransfer.domain.DataTransferRuleRecord;
import com.example.filetransfer.mapper.DataTransferRuleRecordMapper;
import org.springframework.stereotype.Service;

@Service
public class DataTransferRuleRecordServiceImpl extends ServiceImpl<DataTransferRuleRecordMapper, DataTransferRuleRecord> implements DataTransferRuleRecordService {
}
