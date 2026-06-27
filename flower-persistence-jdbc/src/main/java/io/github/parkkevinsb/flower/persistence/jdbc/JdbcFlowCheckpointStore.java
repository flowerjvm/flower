package io.github.parkkevinsb.flower.persistence.jdbc;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.persistence.CheckpointStoreCapabilities;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpoint;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpointStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link FlowCheckpointStore}.
 *
 * <p>This class assumes the standard {@code flower_flow_checkpoint} table is
 * already present. It never creates or migrates schema.
 */
public final class JdbcFlowCheckpointStore implements FlowCheckpointStore {

    private final DataSource dataSource;
    private final JdbcCheckpointDialect dialect;

    public static JdbcFlowCheckpointStore create(DataSource dataSource, JdbcCheckpointDialect dialect) {
        return new JdbcFlowCheckpointStore(dataSource, dialect);
    }

    public JdbcFlowCheckpointStore(DataSource dataSource, JdbcCheckpointDialect dialect) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        if (dialect == null) {
            throw new IllegalArgumentException("dialect must not be null");
        }
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    @Override
    public CheckpointStoreCapabilities capabilities() {
        return CheckpointStoreCapabilities.durableQueryable();
    }

    @Override
    public void save(FlowCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint must not be null");
        }
        Connection c = null;
        PreparedStatement ps = null;
        try {
            c = dataSource.getConnection();
            ps = c.prepareStatement(dialect.upsertSql());
            bindCheckpoint(ps, checkpoint);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save Flower checkpoint: " + checkpoint.flowId(), e);
        } finally {
            close(ps);
            close(c);
        }
    }

    @Override
    public void delete(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        Connection c = null;
        PreparedStatement ps = null;
        try {
            c = dataSource.getConnection();
            ps = c.prepareStatement(dialect.deleteSql());
            bindFlowId(ps, flowId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete Flower checkpoint: " + flowId, e);
        } finally {
            close(ps);
            close(c);
        }
    }

    @Override
    public Optional<FlowCheckpoint> find(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            c = dataSource.getConnection();
            ps = c.prepareStatement(dialect.findSql());
            bindFlowId(ps, flowId);
            rs = ps.executeQuery();
            return rs.next() ? Optional.of(readCheckpoint(rs)) : Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find Flower checkpoint: " + flowId, e);
        } finally {
            close(rs);
            close(ps);
            close(c);
        }
    }

    @Override
    public List<FlowCheckpoint> findActive() {
        return queryMany(dialect.findActiveSql(), null);
    }

    @Override
    public List<FlowCheckpoint> findActiveByWorker(String workerName) {
        if (workerName == null || workerName.isEmpty()) {
            throw new IllegalArgumentException("workerName must not be null or empty");
        }
        return queryMany(dialect.findActiveByWorkerSql(), workerName);
    }

    private List<FlowCheckpoint> queryMany(String sql, String workerName) {
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            c = dataSource.getConnection();
            ps = c.prepareStatement(sql);
            if (workerName != null) {
                ps.setString(1, workerName);
            }
            rs = ps.executeQuery();
            List<FlowCheckpoint> out = new ArrayList<>();
            while (rs.next()) {
                out.add(readCheckpoint(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query Flower checkpoints", e);
        } finally {
            close(rs);
            close(ps);
            close(c);
        }
    }

    private void bindCheckpoint(PreparedStatement ps, FlowCheckpoint checkpoint) throws SQLException {
        int i = 1;
        ps.setString(i++, checkpoint.flowId().flowType());
        ps.setString(i++, checkpoint.flowId().flowKey());
        ps.setString(i++, checkpoint.state().name());
        setNullableString(ps, i++, checkpoint.currentStepId());
        ps.setInt(i++, checkpoint.currentStepNo());
        dialect.bindBoolean(ps, i++, checkpoint.currentStepEntered());
        ps.setString(i++, checkpoint.persistence().name());
        setNullableString(ps, i++, checkpoint.workerName());
        ps.setLong(i++, checkpoint.updatedAtMillis());
        setNullableString(ps, i++, checkpoint.definitionVersion());
        ExecutionContext ctx = checkpoint.executionContext();
        setNullableString(ps, i++, ctx.tenantIdOrNull());
        setNullableString(ps, i++, ctx.userIdOrNull());
        setNullableString(ps, i++, ctx.sessionIdOrNull());
        setNullableString(ps, i++, ctx.runIdOrNull());
        setNullableString(ps, i++, ctx.traceIdOrNull());
        setNullableString(ps, i, ctx.correlationIdOrNull());
    }

    private static void bindFlowId(PreparedStatement ps, FlowId flowId) throws SQLException {
        ps.setString(1, flowId.flowType());
        ps.setString(2, flowId.flowKey());
    }

    private FlowCheckpoint readCheckpoint(ResultSet rs) throws SQLException {
        return new FlowCheckpoint(
                FlowId.of(rs.getString("flow_type"), rs.getString("flow_key")),
                FlowState.valueOf(rs.getString("state")),
                rs.getString("current_step_id"),
                rs.getInt("current_step_no"),
                dialect.readBoolean(rs, "current_step_entered"),
                FlowPersistence.valueOf(rs.getString("persistence")),
                rs.getString("worker_name"),
                rs.getLong("updated_at_millis"),
                rs.getString("definition_version"),
                ExecutionContext.builder()
                        .tenantId(rs.getString("tenant_id"))
                        .userId(rs.getString("user_id"))
                        .sessionId(rs.getString("session_id"))
                        .runId(rs.getString("run_id"))
                        .traceId(rs.getString("trace_id"))
                        .correlationId(rs.getString("correlation_id"))
                        .build());
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void close(AutoCloseable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }
}
