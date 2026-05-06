package com.example.filetransfer.service;

import com.example.filetransfer.domain.ScalableFileRecord;
import com.example.filetransfer.mapper.ScalableFileRecordMapper;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MyBatis-based file-record batch read/write service.
 */
@Service
public class ScalableFileRecordJdbcService {

    private final SqlSessionTemplate batchSqlSessionTemplate;

    public ScalableFileRecordJdbcService(@Qualifier("batchSqlSessionTemplate") SqlSessionTemplate batchSqlSessionTemplate) {
        this.batchSqlSessionTemplate = batchSqlSessionTemplate;
    }

    @Transactional
    public void batchInsert(Long batchId, List<ScalableFileRecord> fileRecords, int batchSize) {
        if (fileRecords.isEmpty()) {
            return;
        }
        ScalableFileRecordMapper mapper = batchSqlSessionTemplate.getMapper(ScalableFileRecordMapper.class);
        int counter = 0;
        for (ScalableFileRecord record : fileRecords) {
            record.setBatchId(batchId);
            mapper.insert(record);
            counter++;
            if (counter % batchSize == 0) {
                batchSqlSessionTemplate.flushStatements();
            }
        }
        batchSqlSessionTemplate.flushStatements();
    }

    @Transactional
    public void batchUpdateStatuses(List<FileStatusUpdateCommand> updates, int batchSize) {
        if (updates.isEmpty()) {
            return;
        }
        ScalableFileRecordMapper mapper = batchSqlSessionTemplate.getMapper(ScalableFileRecordMapper.class);
        int counter = 0;
        for (FileStatusUpdateCommand command : updates) {
            mapper.updateProgressAndStatus(
                    command.recordId(),
                    command.transferredBytes(),
                    command.status(),
                    command.lastError(),
                    command.targetHash()
            );
            counter++;
            if (counter % batchSize == 0) {
                batchSqlSessionTemplate.flushStatements();
            }
        }
        batchSqlSessionTemplate.flushStatements();
    }
}
