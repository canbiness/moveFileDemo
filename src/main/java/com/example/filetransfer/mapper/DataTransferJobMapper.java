package com.example.filetransfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.filetransfer.domain.DataTransferJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataTransferJobMapper extends BaseMapper<DataTransferJob> {
}
