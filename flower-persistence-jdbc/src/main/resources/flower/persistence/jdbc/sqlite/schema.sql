CREATE TABLE flower_flow_checkpoint (
    flow_type TEXT NOT NULL,
    flow_key TEXT NOT NULL,
    state TEXT NOT NULL,
    current_step_id TEXT,
    current_step_no INTEGER NOT NULL,
    current_step_entered INTEGER NOT NULL CHECK (current_step_entered IN (0, 1)),
    persistence TEXT NOT NULL,
    worker_name TEXT,
    updated_at_millis INTEGER NOT NULL,
    definition_version TEXT,
    tenant_id TEXT,
    user_id TEXT,
    session_id TEXT,
    run_id TEXT,
    trace_id TEXT,
    correlation_id TEXT,
    PRIMARY KEY (flow_type, flow_key)
);

CREATE INDEX idx_flower_checkpoint_active
    ON flower_flow_checkpoint (state, updated_at_millis);

CREATE INDEX idx_flower_checkpoint_worker_active
    ON flower_flow_checkpoint (worker_name, state, updated_at_millis);

CREATE INDEX idx_flower_checkpoint_tenant_active
    ON flower_flow_checkpoint (tenant_id, state, updated_at_millis);

CREATE INDEX idx_flower_checkpoint_run
    ON flower_flow_checkpoint (run_id);
