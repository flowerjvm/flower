package io.github.parkkevinsb.flower.persistence.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Built-in JDBC dialects for the standard Flower checkpoint table.
 */
public final class JdbcCheckpointDialects {

    private static final String TABLE = "flower_flow_checkpoint";
    private static final String COLUMNS =
            "flow_type, flow_key, state, current_step_id, current_step_no, "
                    + "current_step_entered, persistence, worker_name, updated_at_millis, definition_version";
    private static final String SELECT =
            "SELECT " + COLUMNS + " FROM " + TABLE;
    private static final String DELETE =
            "DELETE FROM " + TABLE + " WHERE flow_type = ? AND flow_key = ?";
    private static final String FIND =
            SELECT + " WHERE flow_type = ? AND flow_key = ?";
    private static final String FIND_ACTIVE =
            SELECT + " WHERE state IN ('READY', 'RUNNING') ORDER BY updated_at_millis ASC";
    private static final String FIND_ACTIVE_BY_WORKER =
            SELECT + " WHERE state IN ('READY', 'RUNNING') AND worker_name = ? ORDER BY updated_at_millis ASC";

    private JdbcCheckpointDialects() {
    }

    public static JdbcCheckpointDialect postgresql() {
        return new StandardDialect(
                "INSERT INTO " + TABLE + " (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (flow_type, flow_key) DO UPDATE SET "
                        + "state = EXCLUDED.state, "
                        + "current_step_id = EXCLUDED.current_step_id, "
                        + "current_step_no = EXCLUDED.current_step_no, "
                        + "current_step_entered = EXCLUDED.current_step_entered, "
                        + "persistence = EXCLUDED.persistence, "
                        + "worker_name = EXCLUDED.worker_name, "
                        + "updated_at_millis = EXCLUDED.updated_at_millis, "
                        + "definition_version = EXCLUDED.definition_version");
    }

    public static JdbcCheckpointDialect mysql() {
        return new StandardDialect(
                "INSERT INTO " + TABLE + " (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "state = VALUES(state), "
                        + "current_step_id = VALUES(current_step_id), "
                        + "current_step_no = VALUES(current_step_no), "
                        + "current_step_entered = VALUES(current_step_entered), "
                        + "persistence = VALUES(persistence), "
                        + "worker_name = VALUES(worker_name), "
                        + "updated_at_millis = VALUES(updated_at_millis), "
                        + "definition_version = VALUES(definition_version)");
    }

    public static JdbcCheckpointDialect h2() {
        return new StandardDialect(
                "MERGE INTO " + TABLE + " (" + COLUMNS + ") "
                        + "KEY(flow_type, flow_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    }

    public static JdbcCheckpointDialect oracle() {
        return new OracleDialect(
                "MERGE INTO " + TABLE + " t "
                        + "USING (SELECT ? AS flow_type, ? AS flow_key, ? AS state, ? AS current_step_id, "
                        + "? AS current_step_no, ? AS current_step_entered, ? AS persistence, "
                        + "? AS worker_name, ? AS updated_at_millis, ? AS definition_version FROM dual) s "
                        + "ON (t.flow_type = s.flow_type AND t.flow_key = s.flow_key) "
                        + "WHEN MATCHED THEN UPDATE SET "
                        + "t.state = s.state, "
                        + "t.current_step_id = s.current_step_id, "
                        + "t.current_step_no = s.current_step_no, "
                        + "t.current_step_entered = s.current_step_entered, "
                        + "t.persistence = s.persistence, "
                        + "t.worker_name = s.worker_name, "
                        + "t.updated_at_millis = s.updated_at_millis, "
                        + "t.definition_version = s.definition_version "
                        + "WHEN NOT MATCHED THEN INSERT (" + COLUMNS + ") VALUES ("
                        + "s.flow_type, s.flow_key, s.state, s.current_step_id, s.current_step_no, "
                        + "s.current_step_entered, s.persistence, s.worker_name, s.updated_at_millis, "
                        + "s.definition_version)");
    }

    private static class StandardDialect implements JdbcCheckpointDialect {
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

    private static final class OracleDialect extends StandardDialect {
        OracleDialect(String upsertSql) {
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
