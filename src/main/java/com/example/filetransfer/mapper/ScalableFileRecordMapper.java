package com.example.filetransfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.filetransfer.domain.FileTransferStatus;
import com.example.filetransfer.domain.ScalableFileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ScalableFileRecordMapper extends BaseMapper<ScalableFileRecord> {

    @Update("""
            update scalable_file_records
               set transferred_bytes = #{transferredBytes},
                   status = #{status},
                   last_error = #{lastError},
                   target_hash = #{targetHash}
             where id = #{id}
            """)
    int updateProgressAndStatus(@Param("id") Long id,
                                @Param("transferredBytes") long transferredBytes,
                                @Param("status") FileTransferStatus status,
                                @Param("lastError") String lastError,
                                @Param("targetHash") String targetHash);

    @Select("""
            select status as enum_value, count(*) as count_value
              from scalable_file_records
             where task_id = #{taskId}
             group by status
            """)
    List<Map<String, Object>> aggregateStatusCountsByTaskId(@Param("taskId") String taskId);
}
