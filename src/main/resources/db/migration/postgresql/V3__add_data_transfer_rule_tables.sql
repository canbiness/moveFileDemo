CREATE TABLE IF NOT EXISTS d_data_transfer_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(255) NOT NULL,
    path VARCHAR(1024) NOT NULL,
    target_path VARCHAR(1024) NOT NULL,
    location VARCHAR(255),
    from_date INTEGER,
    end_date INTEGER,
    file_name VARCHAR(255),
    folder_name VARCHAR(255),
    regex VARCHAR(512),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS d_data_transfer_rule_suffix (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    suffix VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_d_data_transfer_rule_suffix_rule FOREIGN KEY (rule_id) REFERENCES d_data_transfer_rule (id)
);

CREATE TABLE IF NOT EXISTS d_data_transfer_rule_record (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(255) NOT NULL,
    path VARCHAR(1024) NOT NULL,
    target_path VARCHAR(1024) NOT NULL,
    location VARCHAR(255),
    from_date INTEGER,
    end_date INTEGER,
    file_name VARCHAR(255),
    folder_name VARCHAR(255),
    regex VARCHAR(512),
    suffix_temp TEXT,
    rule_id BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_d_data_transfer_rule_record_rule FOREIGN KEY (rule_id) REFERENCES d_data_transfer_rule (id)
);
