package io.github.parkkevinsb.flower.eventloop.persistence.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEventFlowCheckpointDialectsTest {

    @Test
    void dialects_use_event_checkpoint_table() {
        assertThat(JdbcEventFlowCheckpointDialects.postgresql().upsertSql())
                .contains("flower_event_flow_checkpoint")
                .contains("ON CONFLICT")
                .contains("awaits_payload");
        assertThat(JdbcEventFlowCheckpointDialects.mysql().upsertSql())
                .contains("ON DUPLICATE KEY UPDATE");
        assertThat(JdbcEventFlowCheckpointDialects.oracle().upsertSql())
                .contains("MERGE INTO")
                .contains("FROM dual");
        assertThat(JdbcEventFlowCheckpointDialects.h2().upsertSql())
                .contains("MERGE INTO")
                .contains("KEY(flow_type, flow_key)");
    }

    @Test
    void active_queries_select_ready_and_running_event_checkpoints() {
        assertThat(JdbcEventFlowCheckpointDialects.postgresql().findActiveSql())
                .contains("flower_event_flow_checkpoint")
                .contains("state IN ('READY', 'RUNNING')");
        assertThat(JdbcEventFlowCheckpointDialects.postgresql().findActiveByWorkerSql())
                .contains("worker_name = ?");
    }
}
