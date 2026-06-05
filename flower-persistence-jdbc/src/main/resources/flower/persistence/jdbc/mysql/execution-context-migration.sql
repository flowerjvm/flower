ALTER TABLE flower_flow_checkpoint
    ADD COLUMN tenant_id VARCHAR(100),
    ADD COLUMN user_id VARCHAR(100),
    ADD COLUMN session_id VARCHAR(100),
    ADD COLUMN run_id VARCHAR(100),
    ADD COLUMN trace_id VARCHAR(100),
    ADD COLUMN correlation_id VARCHAR(100);

CREATE INDEX idx_flower_checkpoint_tenant_active
    ON flower_flow_checkpoint (tenant_id, state, updated_at_millis);

CREATE INDEX idx_flower_checkpoint_run
    ON flower_flow_checkpoint (run_id);
