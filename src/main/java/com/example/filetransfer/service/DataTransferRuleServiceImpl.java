package com.example.filetransfer.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.filetransfer.domain.DataTransferRule;
import com.example.filetransfer.mapper.DataTransferRuleMapper;
import org.springframework.stereotype.Service;

@Service
public class DataTransferRuleServiceImpl extends ServiceImpl<DataTransferRuleMapper, DataTransferRule> implements DataTransferRuleService {
}
