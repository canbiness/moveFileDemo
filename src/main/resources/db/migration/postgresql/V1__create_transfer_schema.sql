CREATE TABLE IF NOT EXISTS transfer_tasks (
    id VARCHAR(255) PRIMARY KEY,
    version BIGINT NOT NULL,
    source_path VARCHAR(1024) NOT NULL,
    target_path VARCHAR(1024) NOT NULL,
    transfer_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_bytes BIGINT NOT NULL,
    transferred_bytes BIGINT NOT NULL,
    total_files BIGINT NOT NULL,
    total_batches BIGINT NOT NULL,
    hash_algorithm VARCHAR(128),
    verification_mode VARCHAR(32) NOT NULL,
    source_hash VARCHAR(128),
    target_hash VARCHAR(128),
    retry_count INTEGER NOT NULL,
    last_error VARCHAR(2048),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS transfer_batches (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    batch_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    temperature_tier VARCHAR(16) NOT NULL,
    scheduling_priority INTEGER NOT NULL,
    file_count INTEGER NOT NULL,
    total_bytes BIGINT NOT NULL,
    transferred_bytes BIGINT NOT NULL,
    last_error VARCHAR(2048),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_transfer_batch_task_batch UNIQUE (task_id, batch_number),
    CONSTRAINT fk_transfer_batches_task FOREIGN KEY (task_id) REFERENCES transfer_tasks (id)
);

CREATE TABLE IF NOT EXISTS transfer_progress (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL,
    task_id VARCHAR(255) NOT NULL UNIQUE,
    total_bytes BIGINT NOT NULL,
    transferred_bytes BIGINT NOT NULL,
    file_count INTEGER NOT NULL,
    completed_file_count INTEGER NOT NULL,
    progress_percent DOUBLE PRECISION NOT NULL,
    last_checkpoint_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_transfer_progress_task FOREIGN KEY (task_id) REFERENCES transfer_tasks (id)
);

CREATE TABLE IF NOT EXISTS scalable_file_records (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL,
    batch_id BIGINT NOT NULL,
    relative_path VARCHAR(1024) NOT NULL,
    source_size BIGINT NOT NULL,
    transferred_bytes BIGINT NOT NULL,
    source_last_modified_millis BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_hash VARCHAR(128),
    target_hash VARCHAR(128),
    last_error VARCHAR(2048),
    CONSTRAINT fk_scalable_file_records_task FOREIGN KEY (task_id) REFERENCES transfer_tasks (id),
    CONSTRAINT fk_scalable_file_records_batch FOREIGN KEY (batch_id) REFERENCES transfer_batches (id)
);

CREATE INDEX IF NOT EXISTS idx_transfer_task_status ON transfer_tasks (status);
CREATE INDEX IF NOT EXISTS idx_transfer_task_created_at ON transfer_tasks (created_at);

CREATE INDEX IF NOT EXISTS idx_transfer_batch_task_batch ON transfer_batches (task_id, batch_number);
CREATE INDEX IF NOT EXISTS idx_transfer_batch_task_status ON transfer_batches (task_id, status);
CREATE INDEX IF NOT EXISTS idx_transfer_batch_task_status_batch ON transfer_batches (task_id, status, batch_number);
CREATE INDEX IF NOT EXISTS idx_transfer_batch_task_temp_priority ON transfer_batches (task_id, temperature_tier, scheduling_priority);

CREATE INDEX IF NOT EXISTS idx_transfer_progress_task ON transfer_progress (task_id);

CREATE INDEX IF NOT EXISTS idx_scalable_file_task_batch ON scalable_file_records (task_id, batch_id);
CREATE INDEX IF NOT EXISTS idx_scalable_file_status ON scalable_file_records (status);
CREATE INDEX IF NOT EXISTS idx_scalable_file_task_status ON scalable_file_records (task_id, status);
CREATE INDEX IF NOT EXISTS idx_scalable_file_task_batch_status_id ON scalable_file_records (task_id, batch_id, status, id);

COMMENT ON TABLE transfer_tasks IS '文件迁移任务主表';
COMMENT ON COLUMN transfer_tasks.id IS '任务唯一标识';
COMMENT ON COLUMN transfer_tasks.version IS '乐观锁版本号';
COMMENT ON COLUMN transfer_tasks.source_path IS '源路径';
COMMENT ON COLUMN transfer_tasks.target_path IS '目标路径';
COMMENT ON COLUMN transfer_tasks.transfer_type IS '任务类型';
COMMENT ON COLUMN transfer_tasks.status IS '任务整体状态';
COMMENT ON COLUMN transfer_tasks.total_bytes IS '任务总字节数';
COMMENT ON COLUMN transfer_tasks.transferred_bytes IS '任务已传输字节数';
COMMENT ON COLUMN transfer_tasks.total_files IS '任务总文件数';
COMMENT ON COLUMN transfer_tasks.total_batches IS '任务总批次数';
COMMENT ON COLUMN transfer_tasks.hash_algorithm IS '校验使用的哈希算法';
COMMENT ON COLUMN transfer_tasks.verification_mode IS '校验模式';
COMMENT ON COLUMN transfer_tasks.source_hash IS '源端聚合哈希';
COMMENT ON COLUMN transfer_tasks.target_hash IS '目标端聚合哈希';
COMMENT ON COLUMN transfer_tasks.retry_count IS '任务级重试次数';
COMMENT ON COLUMN transfer_tasks.last_error IS '最近一次错误信息';
COMMENT ON COLUMN transfer_tasks.created_at IS '创建时间';
COMMENT ON COLUMN transfer_tasks.updated_at IS '更新时间';

COMMENT ON TABLE transfer_batches IS '迁移批次表';
COMMENT ON COLUMN transfer_batches.id IS '批次主键';
COMMENT ON COLUMN transfer_batches.version IS '乐观锁版本号';
COMMENT ON COLUMN transfer_batches.task_id IS '所属任务ID';
COMMENT ON COLUMN transfer_batches.batch_number IS '批次序号';
COMMENT ON COLUMN transfer_batches.status IS '批次状态';
COMMENT ON COLUMN transfer_batches.temperature_tier IS '冷热分层标签';
COMMENT ON COLUMN transfer_batches.scheduling_priority IS '调度优先级';
COMMENT ON COLUMN transfer_batches.file_count IS '批次内文件数';
COMMENT ON COLUMN transfer_batches.total_bytes IS '批次总字节数';
COMMENT ON COLUMN transfer_batches.transferred_bytes IS '批次已传输字节数';
COMMENT ON COLUMN transfer_batches.last_error IS '批次错误信息';
COMMENT ON COLUMN transfer_batches.created_at IS '创建时间';
COMMENT ON COLUMN transfer_batches.updated_at IS '更新时间';

COMMENT ON TABLE transfer_progress IS '任务聚合进度表';
COMMENT ON COLUMN transfer_progress.id IS '进度主键';
COMMENT ON COLUMN transfer_progress.version IS '乐观锁版本号';
COMMENT ON COLUMN transfer_progress.task_id IS '所属任务ID';
COMMENT ON COLUMN transfer_progress.total_bytes IS '任务总字节数';
COMMENT ON COLUMN transfer_progress.transferred_bytes IS '当前已传输字节数';
COMMENT ON COLUMN transfer_progress.file_count IS '任务总文件数';
COMMENT ON COLUMN transfer_progress.completed_file_count IS '当前已完成文件数';
COMMENT ON COLUMN transfer_progress.progress_percent IS '进度百分比';
COMMENT ON COLUMN transfer_progress.last_checkpoint_at IS '最近一次检查点时间';
COMMENT ON COLUMN transfer_progress.created_at IS '创建时间';
COMMENT ON COLUMN transfer_progress.updated_at IS '更新时间';

COMMENT ON TABLE scalable_file_records IS '文件明细记录表';
COMMENT ON COLUMN scalable_file_records.id IS '文件记录主键';
COMMENT ON COLUMN scalable_file_records.task_id IS '所属任务ID';
COMMENT ON COLUMN scalable_file_records.batch_id IS '所属批次ID';
COMMENT ON COLUMN scalable_file_records.relative_path IS '相对路径';
COMMENT ON COLUMN scalable_file_records.source_size IS '源文件大小';
COMMENT ON COLUMN scalable_file_records.transferred_bytes IS '已传输字节数';
COMMENT ON COLUMN scalable_file_records.source_last_modified_millis IS '源文件最后修改时间毫秒值';
COMMENT ON COLUMN scalable_file_records.status IS '文件状态';
COMMENT ON COLUMN scalable_file_records.source_hash IS '源文件哈希';
COMMENT ON COLUMN scalable_file_records.target_hash IS '目标文件哈希';
COMMENT ON COLUMN scalable_file_records.last_error IS '文件错误信息';
