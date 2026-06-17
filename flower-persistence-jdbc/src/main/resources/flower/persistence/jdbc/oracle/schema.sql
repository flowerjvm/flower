CREATE TABLE flower_flow_checkpoint (
    flow_type VARCHAR2(100 CHAR) NOT NULL,
    flow_key VARCHAR2(200 CHAR) NOT NULL,
    state VARCHAR2(20 CHAR) NOT NULL,
    current_step_id VARCHAR2(200 CHAR),
    current_step_no NUMBER(10) NOT NULL,
    current_step_entered NUMBER(1) NOT NULL,
    persistence VARCHAR2(20 CHAR) NOT NULL,
    worker_name VARCHAR2(100 CHAR),
    updated_at_millis NUMBER(19) NOT NULL,
    definition_version VARCHAR2(100 CHAR),
    tenant_id VARCHAR2(100 CHAR),
    user_id VARCHAR2(100 CHAR),
    session_id VARCHAR2(100 CHAR),
    run_id VARCHAR2(100 CHAR),
    trace_id VARCHAR2(100 CHAR),
    correlation_id VARCHAR2(100 CHAR),
    CONSTRAINT pk_flower_flow_checkpoint PRIMARY KEY (flow_type, flow_key),
    CONSTRAINT ck_flower_checkpoint_entered CHECK (current_step_entered IN (0, 1))
);

CREATE INDEX idx_flower_checkpoint_active
    ON flower_flow_checkpoint (state, updated_at_millis);

CREATE INDEX idx_flower_checkpoint_worker_active
    ON flower_flow_checkpoint (worker_name, state, updated_at_millis);

CREATE INDEX idx_flower_checkpoint_tenant_active
    ON flower_flow_checkpoint (tenant_id, state, updated_at_millis);

CREATE INDEX idx_flower_checkpoint_run
    ON flower_flow_checkpoint (run_id);

CREATE TABLE flower_event_flow_checkpoint (
    flow_type VARCHAR2(100 CHAR) NOT NULL,
    flow_key VARCHAR2(200 CHAR) NOT NULL,
    state VARCHAR2(20 CHAR) NOT NULL,
    current_step_id VARCHAR2(200 CHAR),
    current_step_entered NUMBER(1) NOT NULL,
    persistence VARCHAR2(20 CHAR) NOT NULL,
    worker_name VARCHAR2(100 CHAR),
    updated_at_millis NUMBER(19) NOT NULL,
    definition_version VARCHAR2(100 CHAR),
    tenant_id VARCHAR2(100 CHAR),
    user_id VARCHAR2(100 CHAR),
    session_id VARCHAR2(100 CHAR),
    run_id VARCHAR2(100 CHAR),
    trace_id VARCHAR2(100 CHAR),
    correlation_id VARCHAR2(100 CHAR),
    await_generation NUMBER(19) NOT NULL,
    awaits_payload CLOB,
    CONSTRAINT pk_flower_event_flow_checkpoint PRIMARY KEY (flow_type, flow_key),
    CONSTRAINT ck_flower_event_checkpoint_entered CHECK (current_step_entered IN (0, 1))
);

CREATE INDEX idx_flower_event_checkpoint_active
    ON flower_event_flow_checkpoint (state, updated_at_millis);

CREATE INDEX idx_flower_event_checkpoint_worker_active
    ON flower_event_flow_checkpoint (worker_name, state, updated_at_millis);

CREATE INDEX idx_flower_event_checkpoint_tenant_active
    ON flower_event_flow_checkpoint (tenant_id, state, updated_at_millis);

CREATE INDEX idx_flower_event_checkpoint_run
    ON flower_event_flow_checkpoint (run_id);
