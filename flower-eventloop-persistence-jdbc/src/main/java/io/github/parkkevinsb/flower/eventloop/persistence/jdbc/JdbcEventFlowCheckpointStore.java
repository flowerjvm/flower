package io.github.parkkevinsb.flower.eventloop.persistence.jdbc;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.eventloop.persistence.EventAwaitCheckpoint;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpoint;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpointStore;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link EventFlowCheckpointStore}.
 *
 * <p>This class assumes the standard {@code flower_event_flow_checkpoint} table
 * is already present. It never creates or migrates schema.
 */
public final class JdbcEventFlowCheckpointStore implements EventFlowCheckpointStore {

    private final DataSource dataSource;
    private final JdbcEventFlowCheckpointDialect dialect;

    public static JdbcEventFlowCheckpointStore create(
            DataSource dataSource,
            JdbcEventFlowCheckpointDialect dialect) {
        return new JdbcEventFlowCheckpointStore(dataSource, dialect);
    }

    public JdbcEventFlowCheckpointStore(DataSource dataSource, JdbcEventFlowCheckpointDialect dialect) {
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
    public void save(EventFlowCheckpoint checkpoint) {
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
            throw new IllegalStateException("Failed to save Flower event-flow checkpoint: "
                    + checkpoint.flowId(), e);
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
            throw new IllegalStateException("Failed to delete Flower event-flow checkpoint: " + flowId, e);
        } finally {
            close(ps);
            close(c);
        }
    }

    @Override
    public Optional<EventFlowCheckpoint> find(FlowId flowId) {
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
            throw new IllegalStateException("Failed to find Flower event-flow checkpoint: " + flowId, e);
        } finally {
            close(rs);
            close(ps);
            close(c);
        }
    }

    @Override
    public List<EventFlowCheckpoint> findActive() {
        return queryMany(dialect.findActiveSql(), null);
    }

    @Override
    public List<EventFlowCheckpoint> findActiveByWorker(String workerName) {
        if (workerName == null || workerName.isEmpty()) {
            throw new IllegalArgumentException("workerName must not be null or empty");
        }
        return queryMany(dialect.findActiveByWorkerSql(), workerName);
    }

    private List<EventFlowCheckpoint> queryMany(String sql, String workerName) {
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
            List<EventFlowCheckpoint> out = new ArrayList<>();
            while (rs.next()) {
                out.add(readCheckpoint(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query Flower event-flow checkpoints", e);
        } finally {
            close(rs);
            close(ps);
            close(c);
        }
    }

    private void bindCheckpoint(PreparedStatement ps, EventFlowCheckpoint checkpoint) throws SQLException {
        int i = 1;
        ps.setString(i++, checkpoint.flowId().flowType());
        ps.setString(i++, checkpoint.flowId().flowKey());
        ps.setString(i++, checkpoint.state().name());
        setNullableString(ps, i++, checkpoint.currentStepId());
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
        setNullableString(ps, i++, ctx.correlationIdOrNull());
        ps.setLong(i++, checkpoint.awaitGeneration());
        setNullableString(ps, i, encodeAwaits(checkpoint.awaits()));
    }

    private static void bindFlowId(PreparedStatement ps, FlowId flowId) throws SQLException {
        ps.setString(1, flowId.flowType());
        ps.setString(2, flowId.flowKey());
    }

    private EventFlowCheckpoint readCheckpoint(ResultSet rs) throws SQLException {
        return new EventFlowCheckpoint(
                FlowId.of(rs.getString("flow_type"), rs.getString("flow_key")),
                FlowState.valueOf(rs.getString("state")),
                rs.getString("current_step_id"),
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
                        .build(),
                rs.getLong("await_generation"),
                decodeAwaits(rs.getString("awaits_payload")));
    }

    private static String encodeAwaits(List<EventAwaitCheckpoint> awaits) {
        if (awaits == null || awaits.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (EventAwaitCheckpoint await : awaits) {
            if (await.type() == EventAwaitCheckpoint.Type.EVENT) {
                out.append("EVENT\t").append(encodeString(await.eventTypeName())).append('\n');
            } else if (await.type() == EventAwaitCheckpoint.Type.SIGNAL) {
                out.append("SIGNAL\t")
                        .append(encodeString(await.signalName()))
                        .append('\t')
                        .append(encodeString(await.signalKey()))
                        .append('\n');
            } else if (await.type() == EventAwaitCheckpoint.Type.DEADLINE) {
                out.append("DEADLINE\t").append(await.deadlineAtMillis()).append('\n');
            } else {
                throw new IllegalArgumentException("unknown event await checkpoint type: " + await.type());
            }
        }
        return out.toString();
    }

    private static List<EventAwaitCheckpoint> decodeAwaits(String payload) {
        if (payload == null || payload.isEmpty()) {
            return Collections.emptyList();
        }
        List<EventAwaitCheckpoint> out = new ArrayList<>();
        String[] lines = payload.split("\\n", -1);
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            int sep = line.indexOf('\t');
            if (sep < 0) {
                throw new IllegalArgumentException("invalid event await checkpoint payload line: " + line);
            }
            String type = line.substring(0, sep);
            String value = line.substring(sep + 1);
            if ("EVENT".equals(type)) {
                out.add(EventAwaitCheckpoint.event(decodeString(value)));
            } else if ("SIGNAL".equals(type)) {
                int secondSep = value.indexOf('\t');
                if (secondSep < 0) {
                    throw new IllegalArgumentException("invalid signal await checkpoint payload line: " + line);
                }
                out.add(EventAwaitCheckpoint.signal(
                        decodeString(value.substring(0, secondSep)),
                        decodeString(value.substring(secondSep + 1))));
            } else if ("DEADLINE".equals(type)) {
                out.add(EventAwaitCheckpoint.deadline(Long.parseLong(value)));
            } else {
                throw new IllegalArgumentException("invalid event await checkpoint payload type: " + type);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static String encodeString(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeString(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
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
