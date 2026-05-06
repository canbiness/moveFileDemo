package com.example.filetransfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.TransferTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TransferTaskMapper extends BaseMapper<TransferTask> {

    @Update("""
            update transfer_tasks
               set status = #{status},
                   last_error = #{lastError},
                   updated_at = now(),
                   version = version + 1
             where id = #{taskId}
            """)
    int updateStatusAndError(@Param("taskId") String taskId,
                             @Param("status") TransferStatus status,
                             @Param("lastError") String lastError);
}
