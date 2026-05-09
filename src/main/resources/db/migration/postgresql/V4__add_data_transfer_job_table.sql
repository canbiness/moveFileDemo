CREATE TABLE IF NOT EXISTS d_data_transfer_job (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    task_id VARCHAR(255),
    job_cron VARCHAR(255),
    status INTEGER NOT NULL,
    sort_number INTEGER,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_d_data_transfer_job_rule FOREIGN KEY (rule_id) REFERENCES d_data_transfer_rule (id)
);

CREATE INDEX IF NOT EXISTS idx_d_data_transfer_job_rule_id ON d_data_transfer_job (rule_id);
CREATE INDEX IF NOT EXISTS idx_d_data_transfer_job_status ON d_data_transfer_job (status);
