package com.example.filetransfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.filetransfer.domain.BatchStatus;
import com.example.filetransfer.domain.TransferBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface TransferBatchMapper extends BaseMapper<TransferBatch> {

    @Update("""
            update transfer_batches
               set status = #{status},
                   transferred_bytes = #{transferredBytes},
                   last_error = #{lastError},
                   updated_at = now(),
                   version = version + 1
             where id = #{id}
            """)
    int updateBatchProgressAndStatus(@Param("id") Long id,
                                     @Param("status") BatchStatus status,
                                     @Param("transferredBytes") long transferredBytes,
                                     @Param("lastError") String lastError);

    @Select("""
            select status as enum_value, count(*) as count_value
              from transfer_batches
             where task_id = #{taskId}
             group by status
            """)
    List<Map<String, Object>> aggregateStatusCountsByTaskId(@Param("taskId") String taskId);

    @Select("""
            select temperature_tier as enum_value, count(*) as count_value
              from transfer_batches
             where task_id = #{taskId}
             group by temperature_tier
            """)
    List<Map<String, Object>> aggregateTemperatureCountsByTaskId(@Param("taskId") String taskId);

    @Select("""
            <script>
            select *
              from transfer_batches
             where task_id = #{taskId}
               and status in
               <foreach collection="statuses" item="status" open="(" separator="," close=")">
                 #{status}
               </foreach>
               and batch_number > #{batchCursor}
             order by batch_number asc
             limit #{limit}
            </script>
            """)
    List<TransferBatch> selectNextBatches(@Param("taskId") String taskId,
                                          @Param("statuses") List<BatchStatus> statuses,
                                          @Param("batchCursor") int batchCursor,
                                          @Param("limit") int limit);

    @Select("""
            <script>
            select *
              from transfer_batches
             where id in
             <foreach collection="batchIds" item="batchId" open="(" separator="," close=")">
                 #{batchId}
             </foreach>
            </script>
            """)
    List<TransferBatch> selectByIds(@Param("batchIds") List<Long> batchIds);
}
