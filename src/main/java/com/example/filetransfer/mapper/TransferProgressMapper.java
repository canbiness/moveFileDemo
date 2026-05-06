package com.example.filetransfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.filetransfer.domain.TransferProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TransferProgressMapper extends BaseMapper<TransferProgress> {

    @Update("""
            update transfer_progress
               set transferred_bytes = transferred_bytes + #{bytesDelta},
                   completed_file_count = completed_file_count + #{filesDelta},
                   progress_percent = case
                       when total_bytes = 0 then 100
                       else ((transferred_bytes + #{bytesDelta}) * 100.0 / total_bytes)
                   end,
                   last_checkpoint_at = #{checkpoint},
                   updated_at = now(),
                   version = version + 1
             where task_id = #{taskId}
            """)
    int incrementProgress(@Param("taskId") String taskId,
                          @Param("bytesDelta") long bytesDelta,
                          @Param("filesDelta") int filesDelta,
                          @Param("checkpoint") LocalDateTime checkpoint);
}
