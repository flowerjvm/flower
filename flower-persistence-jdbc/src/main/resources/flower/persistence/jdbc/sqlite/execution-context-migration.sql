ALTER TABLE flower_flow_checkpoint ADD COLUMN tenant_id TEXT;
ALTER TABLE flower_flow_checkpoint ADD COLUMN user_id TEXT;
ALTER TABLE flower_flow_checkpoint ADD COLUMN session_id TEXT;
ALTER TABLE flower_flow_checkpoint ADD COLUMN run_id TEXT;
ALTER TABLE flower_flow_checkpoint ADD COLUMN trace_id TEXT;
ALTER TABLE flower_flow_checkpoint ADD COLUMN correlation_id TEXT;

CREATE INDEX idx_flower_checkpoint_tenant_active
    ON flower_flow_checkpoint (tenant_id, state, updated_at_millis);

CREATE INDEX idx_flower_checkpoint_run
    ON flower_flow_checkpoint (run_id);
