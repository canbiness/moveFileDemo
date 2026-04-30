package com.example.filetransfer.service;

import com.example.filetransfer.domain.ScalableFileRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * JDBC-based file-record batch read/write service.
 */
@Service
@RequiredArgsConstructor
public class ScalableFileRecordJdbcService {

    private static final String INSERT_SQL = """
            insert into scalable_file_records
                (task_id, batch_id, relative_path, source_size, transferred_bytes,
                 source_last_modified_millis, status, source_hash, target_hash, last_error)
            values
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_STATUS_SQL = """
            update scalable_file_records
               set transferred_bytes = ?,
                   status = ?,
                   last_error = ?,
                   target_hash = ?
             where id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void batchInsert(Long batchId, List<ScalableFileRecord> fileRecords, int batchSize) {
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                fileRecords,
                batchSize,
                new ParameterizedPreparedStatementSetter<>() {
                    @Override
                    public void setValues(PreparedStatement ps, ScalableFileRecord record) throws SQLException {
                        ps.setString(1, record.getTaskId());
                        ps.setLong(2, batchId);
                        ps.setString(3, record.getRelativePath());
                        ps.setLong(4, record.getSourceSize());
                        ps.setLong(5, record.getTransferredBytes());
                        ps.setLong(6, record.getSourceLastModifiedMillis());
                        ps.setString(7, record.getStatus().name());
                        ps.setString(8, record.getSourceHash());
                        ps.setString(9, record.getTargetHash());
                        ps.setString(10, record.getLastError());
                    }
                }
        );
    }

    @Transactional
    public void batchUpdateStatuses(List<FileStatusUpdateCommand> updates, int batchSize) {
        jdbcTemplate.batchUpdate(
                UPDATE_STATUS_SQL,
                updates,
                batchSize,
                new ParameterizedPreparedStatementSetter<>() {
                    @Override
                    public void setValues(PreparedStatement ps, FileStatusUpdateCommand command) throws SQLException {
                        ps.setLong(1, command.transferredBytes());
                        ps.setString(2, command.status().name());
                        ps.setString(3, command.lastError());
                        ps.setString(4, command.targetHash());
                        ps.setLong(5, command.recordId());
                    }
                }
        );
    }
}
