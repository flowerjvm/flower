package io.github.parkkevinsb.flower.persistence.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCheckpointDialectsTest {

    @Test
    void dialects_expose_vendor_specific_upsert_sql() {
        assertThat(JdbcCheckpointDialects.postgresql().upsertSql())
                .contains("ON CONFLICT");
        assertThat(JdbcCheckpointDialects.mysql().upsertSql())
                .contains("ON DUPLICATE KEY UPDATE");
        assertThat(JdbcCheckpointDialects.oracle().upsertSql())
                .contains("MERGE INTO")
                .contains("FROM dual");
        assertThat(JdbcCheckpointDialects.h2().upsertSql())
                .contains("MERGE INTO")
                .contains("KEY(flow_type, flow_key)");
    }

    @Test
    void active_queries_select_ready_and_running_checkpoints() {
        assertThat(JdbcCheckpointDialects.postgresql().findActiveSql())
                .contains("state IN ('READY', 'RUNNING')");
        assertThat(JdbcCheckpointDialects.postgresql().findActiveByWorkerSql())
                .contains("worker_name = ?");
    }
}
