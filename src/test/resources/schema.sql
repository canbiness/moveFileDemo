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
