ALTER TABLE flower_flow_checkpoint ADD (
    tenant_id VARCHAR2(100 CHAR),
    user_id VARCHAR2(100 CHAR),
    session_id VARCHAR2(100 CHAR),
    run_id VARCHAR2(100 CHAR),
    trace_id VARCHAR2(100 CHAR),
    correlation_id VARCHAR2(100 CHAR)
);

CREATE INDEX idx_flower_checkpoint_tenant_active
    ON flower_flow_checkpoint (tenant_id, state, updated_at_millis);

CREATE INDEX idx_flower_checkpoint_run
    ON flower_flow_checkpoint (run_id);
