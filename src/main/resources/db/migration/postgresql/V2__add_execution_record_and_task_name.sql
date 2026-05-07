ALTER TABLE transfer_tasks
    ADD COLUMN IF NOT EXISTS task_name VARCHAR(255);

CREATE TABLE IF NOT EXISTS transfer_execution_records (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    task_name VARCHAR(255),
    scanned_file_count BIGINT NOT NULL,
    moved_file_count BIGINT NOT NULL,
    moved_file_size BIGINT NOT NULL,
    started_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    duration_millis BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_error VARCHAR(2048),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_transfer_execution_records_task FOREIGN KEY (task_id) REFERENCES transfer_tasks (id)
);

CREATE INDEX IF NOT EXISTS idx_transfer_execution_task_created_at
    ON transfer_execution_records (task_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_transfer_execution_status
    ON transfer_execution_records (status);
