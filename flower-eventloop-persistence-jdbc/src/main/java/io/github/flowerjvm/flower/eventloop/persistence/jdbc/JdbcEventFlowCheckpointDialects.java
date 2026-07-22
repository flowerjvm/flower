package io.github.flowerjvm.flower.eventloop.persistence.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Built-in JDBC dialects for the standard event-flow checkpoint table.
 */
public final class JdbcEventFlowCheckpointDialects {

    private static final String TABLE = "flower_event_flow_checkpoint";
    private static final String COLUMNS =
            "flow_type, flow_key, state, current_step_id, current_step_entered, "
                    + "persistence, worker_name, updated_at_millis, definition_version, "
                    + "tenant_id, user_id, session_id, run_id, trace_id, correlation_id, "
                    + "await_generation, awaits_payload";
    private static final String SELECT =
            "SELECT " + COLUMNS + " FROM " + TABLE;
    private static final String DELETE =
            "DELETE FROM " + TABLE + " WHERE flow_type = ? AND flow_key = ?";
    private static final String FIND =
            SELECT + " WHERE flow_type = ? AND flow_key = ?";
    private static final String FIND_ACTIVE =
            SELECT + " WHERE state = 'RUNNING' ORDER BY updated_at_millis ASC";
    private static final String FIND_ACTIVE_BY_WORKER =
            SELECT + " WHERE state = 'RUNNING' AND worker_name = ? ORDER BY updated_at_millis ASC";

    private JdbcEventFlowCheckpointDialects() {
    }

    public static JdbcEventFlowCheckpointDialect postgresql() {
        return new StandardDialect(
                onConflictUpsertSql());
    }

    /**
     * SQLite dialect for a local, file-backed event-flow checkpoint store.
     */
    public static JdbcEventFlowCheckpointDialect sqlite() {
        return new SQLiteEventDialect(onConflictUpsertSql());
    }

    public static JdbcEventFlowCheckpointDialect mysql() {
        return new StandardDialect(
                "INSERT INTO " + TABLE + " (" + COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "state = VALUES(state), "
                        + "current_step_id = VALUES(current_step_id), "
                        + "current_step_entered = VALUES(current_step_entered), "
                        + "persistence = VALUES(persistence), "
                        + "worker_name = VALUES(worker_name), "
                        + "updated_at_millis = VALUES(updated_at_millis), "
                        + "definition_version = VALUES(definition_version), "
                        + "tenant_id = VALUES(tenant_id), "
                        + "user_id = VALUES(user_id), "
                        + "session_id = VALUES(session_id), "
                        + "run_id = VALUES(run_id), "
                        + "trace_id = VALUES(trace_id), "
                        + "correlation_id = VALUES(correlation_id), "
                        + "await_generation = VALUES(await_generation), "
                        + "awaits_payload = VALUES(awaits_payload)");
    }

    public static JdbcEventFlowCheckpointDialect h2() {
        return new StandardDialect(
                "MERGE INTO " + TABLE + " (" + COLUMNS + ") "
                        + "KEY(flow_type, flow_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    }

    public static JdbcEventFlowCheckpointDialect oracle() {
        return new OracleEventDialect(
                "MERGE INTO " + TABLE + " t "
                        + "USING (SELECT ? AS flow_type, ? AS flow_key, ? AS state, ? AS current_step_id, "
                        + "? AS current_step_entered, ? AS persistence, ? AS worker_name, "
                        + "? AS updated_at_millis, ? AS definition_version, ? AS tenant_id, ? AS user_id, "
                        + "? AS session_id, ? AS run_id, ? AS trace_id, ? AS correlation_id, "
                        + "? AS await_generation, ? AS awaits_payload FROM dual) s "
                        + "ON (t.flow_type = s.flow_type AND t.flow_key = s.flow_key) "
                        + "WHEN MATCHED THEN UPDATE SET "
                        + "t.state = s.state, "
                        + "t.current_step_id = s.current_step_id, "
                        + "t.current_step_entered = s.current_step_entered, "
                        + "t.persistence = s.persistence, "
                        + "t.worker_name = s.worker_name, "
                        + "t.updated_at_millis = s.updated_at_millis, "
                        + "t.definition_version = s.definition_version, "
                        + "t.tenant_id = s.tenant_id, "
                        + "t.user_id = s.user_id, "
                        + "t.session_id = s.session_id, "
                        + "t.run_id = s.run_id, "
                        + "t.trace_id = s.trace_id, "
                        + "t.correlation_id = s.correlation_id, "
                        + "t.await_generation = s.await_generation, "
                        + "t.awaits_payload = s.awaits_payload "
                        + "WHEN NOT MATCHED THEN INSERT (" + COLUMNS + ") VALUES ("
                        + "s.flow_type, s.flow_key, s.state, s.current_step_id, s.current_step_entered, "
                        + "s.persistence, s.worker_name, s.updated_at_millis, s.definition_version, "
                        + "s.tenant_id, s.user_id, s.session_id, s.run_id, s.trace_id, s.correlation_id, "
                        + "s.await_generation, s.awaits_payload)");
    }

    private static String onConflictUpsertSql() {
        return "INSERT INTO " + TABLE + " (" + COLUMNS + ") "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (flow_type, flow_key) DO UPDATE SET "
                + "state = EXCLUDED.state, "
                + "current_step_id = EXCLUDED.current_step_id, "
                + "current_step_entered = EXCLUDED.current_step_entered, "
                + "persistence = EXCLUDED.persistence, "
                + "worker_name = EXCLUDED.worker_name, "
                + "updated_at_millis = EXCLUDED.updated_at_millis, "
                + "definition_version = EXCLUDED.definition_version, "
                + "tenant_id = EXCLUDED.tenant_id, "
                + "user_id = EXCLUDED.user_id, "
                + "session_id = EXCLUDED.session_id, "
                + "run_id = EXCLUDED.run_id, "
                + "trace_id = EXCLUDED.trace_id, "
                + "correlation_id = EXCLUDED.correlation_id, "
                + "await_generation = EXCLUDED.await_generation, "
                + "awaits_payload = EXCLUDED.awaits_payload";
    }

    private static class StandardDialect implements JdbcEventFlowCheckpointDialect {
        private final String upsertSql;

        StandardDialect(String upsertSql) {
            this.upsertSql = upsertSql;
        }

        @Override
        public String upsertSql() {
            return upsertSql;
        }

        @Override
        public String deleteSql() {
            return DELETE;
        }

        @Override
        public String findSql() {
            return FIND;
        }

        @Override
        public String findActiveSql() {
            return FIND_ACTIVE;
        }

        @Override
        public String findActiveByWorkerSql() {
            return FIND_ACTIVE_BY_WORKER;
        }
    }

    private static final class OracleEventDialect extends StandardDialect {
        OracleEventDialect(String upsertSql) {
            super(upsertSql);
        }

        @Override
        public void bindBoolean(PreparedStatement ps, int index, boolean value)
                throws SQLException {
            ps.setInt(index, value ? 1 : 0);
        }

        @Override
        public boolean readBoolean(ResultSet rs, String column) throws SQLException {
            return rs.getInt(column) != 0;
        }
    }

    private static final class SQLiteEventDialect extends StandardDialect {
        SQLiteEventDialect(String upsertSql) {
            super(upsertSql);
        }

        @Override
        public void bindBoolean(PreparedStatement ps, int index, boolean value) throws SQLException {
            ps.setInt(index, value ? 1 : 0);
        }

        @Override
        public boolean readBoolean(ResultSet rs, String column) throws SQLException {
            return rs.getInt(column) != 0;
        }
    }
}
