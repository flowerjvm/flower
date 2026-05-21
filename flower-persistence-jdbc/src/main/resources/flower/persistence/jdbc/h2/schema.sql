CREATE TABLE flower_flow_checkpoint (
    flow_type VARCHAR(100) NOT NULL,
    flow_key VARCHAR(200) NOT NULL,
    state VARCHAR(20) NOT NULL,
    current_step_id VARCHAR(200),
    current_step_no INTEGER NOT NULL,
    current_step_entered BOOLEAN NOT NULL,
    persistence VARCHAR(20) NOT NULL,
    worker_name VARCHAR(100),
    updated_at_millis BIGINT NOT NULL,
    definition_version VARCHAR(100),
    PRIMARY KEY (flow_type, flow_key)
);

CREATE INDEX idx_flower_checkpoint_active
    ON flower_flow_checkpoint (state, updated_at_millis);

CREATE INDEX idx_flower_checkpoint_worker_active
    ON flower_flow_checkpoint (worker_name, state, updated_at_millis);
