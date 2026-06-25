package io.github.parkkevinsb.flower.eventloop.persistence.jdbc;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.eventloop.persistence.EventAwaitCheckpoint;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpoint;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEventFlowCheckpointStoreTest {

    @Test
    void save_binds_event_checkpoint_fields_in_order() {
        RecordingDataSource ds = new RecordingDataSource();
        JdbcEventFlowCheckpointStore store = JdbcEventFlowCheckpointStore.create(
                ds, JdbcEventFlowCheckpointDialects.postgresql());

        store.save(new EventFlowCheckpoint(
                FlowId.of("agent", "A-1"),
                FlowState.RUNNING,
                "await-tool",
                true,
                FlowPersistence.DURABLE,
                "event-worker-a",
                1234L,
                "v1",
                ExecutionContext.builder()
                        .tenantId("tenant-a")
                        .userId("user-1")
                        .sessionId("session-1")
                        .runId("run-1")
                        .traceId("trace-1")
                        .correlationId("corr-1")
                        .build(),
                9L,
                Arrays.asList(
                        EventAwaitCheckpoint.event(String.class.getName()),
                        EventAwaitCheckpoint.signal("tool-call", "call-1"),
                        EventAwaitCheckpoint.deadline(5_000L))));

        assertThat(ds.sql).contains("flower_event_flow_checkpoint").contains("ON CONFLICT");
        assertThat(ds.params).containsEntry(1, "agent");
        assertThat(ds.params).containsEntry(2, "A-1");
        assertThat(ds.params).containsEntry(3, "RUNNING");
        assertThat(ds.params).containsEntry(4, "await-tool");
        assertThat(ds.params).containsEntry(5, true);
        assertThat(ds.params).containsEntry(6, "DURABLE");
        assertThat(ds.params).containsEntry(7, "event-worker-a");
        assertThat(ds.params).containsEntry(8, 1234L);
        assertThat(ds.params).containsEntry(9, "v1");
        assertThat(ds.params).containsEntry(10, "tenant-a");
        assertThat(ds.params).containsEntry(11, "user-1");
        assertThat(ds.params).containsEntry(12, "session-1");
        assertThat(ds.params).containsEntry(13, "run-1");
        assertThat(ds.params).containsEntry(14, "trace-1");
        assertThat(ds.params).containsEntry(15, "corr-1");
        assertThat(ds.params).containsEntry(16, 9L);
        assertThat((String) ds.params.get(17))
                .contains("EVENT\t")
                .contains("SIGNAL\t")
                .contains("DEADLINE\t5000");
        assertThat(ds.updateCount).isEqualTo(1);
    }

    @Test
    void find_reads_event_checkpoint_row() {
        RecordingDataSource ds = new RecordingDataSource();
        ds.rows.add(row("agent", "A-1", "RUNNING", "await-tool",
                true, "DURABLE", "event-worker-a", 4321L, "v2",
                "tenant-a", "user-1", "session-1", "run-1", "trace-1", "corr-1",
                11L,
                "EVENT\t" + encode(String.class.getName()) + "\n"
                        + "SIGNAL\t" + encode("tool-call") + "\t" + encode("call-1") + "\n"
                        + "DEADLINE\t7000\n"));
        JdbcEventFlowCheckpointStore store = JdbcEventFlowCheckpointStore.create(
                ds, JdbcEventFlowCheckpointDialects.postgresql());

        Optional<EventFlowCheckpoint> found = store.find(FlowId.of("agent", "A-1"));

        assertThat(ds.sql).contains("WHERE flow_type = ? AND flow_key = ?");
        assertThat(ds.params).containsEntry(1, "agent");
        assertThat(ds.params).containsEntry(2, "A-1");
        assertThat(found).isPresent();
        assertThat(found.get().flowId()).isEqualTo(FlowId.of("agent", "A-1"));
        assertThat(found.get().state()).isEqualTo(FlowState.RUNNING);
        assertThat(found.get().currentStepId()).isEqualTo("await-tool");
        assertThat(found.get().currentStepEntered()).isTrue();
        assertThat(found.get().persistence()).isEqualTo(FlowPersistence.DURABLE);
        assertThat(found.get().workerName()).isEqualTo("event-worker-a");
        assertThat(found.get().updatedAtMillis()).isEqualTo(4321L);
        assertThat(found.get().definitionVersion()).isEqualTo("v2");
        assertThat(found.get().executionContext().tenantId()).contains("tenant-a");
        assertThat(found.get().executionContext().userId()).contains("user-1");
        assertThat(found.get().executionContext().sessionId()).contains("session-1");
        assertThat(found.get().executionContext().runId()).contains("run-1");
        assertThat(found.get().executionContext().traceId()).contains("trace-1");
        assertThat(found.get().executionContext().correlationId()).contains("corr-1");
        assertThat(found.get().awaitGeneration()).isEqualTo(11L);
        assertThat(found.get().awaits()).hasSize(3);
        assertThat(found.get().awaits().get(0).type()).isEqualTo(EventAwaitCheckpoint.Type.EVENT);
        assertThat(found.get().awaits().get(0).eventTypeName()).isEqualTo(String.class.getName());
        assertThat(found.get().awaits().get(1).type()).isEqualTo(EventAwaitCheckpoint.Type.SIGNAL);
        assertThat(found.get().awaits().get(1).signalName()).isEqualTo("tool-call");
        assertThat(found.get().awaits().get(1).signalKey()).isEqualTo("call-1");
        assertThat(found.get().awaits().get(2).type()).isEqualTo(EventAwaitCheckpoint.Type.DEADLINE);
        assertThat(found.get().awaits().get(2).deadlineAtMillis()).isEqualTo(7_000L);
    }

    @Test
    void findActiveByWorker_binds_worker_name() {
        RecordingDataSource ds = new RecordingDataSource();
        ds.rows.add(row("agent", "A-1", "RUNNING", "await-tool",
                true, "DURABLE", "event-worker-a", 1L, null,
                null, null, null, null, null, null,
                1L, ""));
        JdbcEventFlowCheckpointStore store = JdbcEventFlowCheckpointStore.create(
                ds, JdbcEventFlowCheckpointDialects.postgresql());

        List<EventFlowCheckpoint> found = store.findActiveByWorker("event-worker-a");

        assertThat(ds.sql).contains("flower_event_flow_checkpoint").contains("worker_name = ?");
        assertThat(ds.params).containsEntry(1, "event-worker-a");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).flowId()).isEqualTo(FlowId.of("agent", "A-1"));
    }

    @Test
    void oracle_dialect_binds_boolean_as_number() {
        RecordingDataSource ds = new RecordingDataSource();
        JdbcEventFlowCheckpointStore store = JdbcEventFlowCheckpointStore.create(
                ds, JdbcEventFlowCheckpointDialects.oracle());

        store.save(new EventFlowCheckpoint(
                FlowId.of("agent", "A-1"),
                FlowState.RUNNING,
                "await-tool",
                true,
                FlowPersistence.DURABLE,
                "event-worker-a",
                1234L,
                "v1",
                ExecutionContext.empty(),
                1L,
                Arrays.asList(EventAwaitCheckpoint.event(String.class.getName()))));

        assertThat(ds.params).containsEntry(5, 1);
    }

    private static Map<String, Object> row(
            String flowType,
            String flowKey,
            String state,
            String currentStepId,
            boolean currentStepEntered,
            String persistence,
            String workerName,
            long updatedAtMillis,
            String definitionVersion,
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String traceId,
            String correlationId,
            long awaitGeneration,
            String awaitsPayload) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("flow_type", flowType);
        row.put("flow_key", flowKey);
        row.put("state", state);
        row.put("current_step_id", currentStepId);
        row.put("current_step_entered", currentStepEntered);
        row.put("persistence", persistence);
        row.put("worker_name", workerName);
        row.put("updated_at_millis", updatedAtMillis);
        row.put("definition_version", definitionVersion);
        row.put("tenant_id", tenantId);
        row.put("user_id", userId);
        row.put("session_id", sessionId);
        row.put("run_id", runId);
        row.put("trace_id", traceId);
        row.put("correlation_id", correlationId);
        row.put("await_generation", awaitGeneration);
        row.put("awaits_payload", awaitsPayload);
        return row;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingDataSource implements DataSource {
        private final Map<Integer, Object> params = new LinkedHashMap<>();
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private String sql;
        private int updateCount;

        @Override
        public Connection getConnection() {
            return proxy(Connection.class, (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    sql = (String) args[0];
                    return preparedStatement();
                }
                if ("close".equals(method.getName())) {
                    return null;
                }
                return defaultValue(method);
            });
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        private PreparedStatement preparedStatement() {
            return proxy(PreparedStatement.class, (proxy, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer) {
                    params.put((Integer) args[0], "setNull".equals(name) ? null : args[1]);
                    return null;
                }
                if ("executeUpdate".equals(name)) {
                    updateCount++;
                    return 1;
                }
                if ("executeQuery".equals(name)) {
                    return resultSet();
                }
                if ("close".equals(name)) {
                    return null;
                }
                return defaultValue(method);
            });
        }

        private ResultSet resultSet() {
            return proxy(ResultSet.class, new InvocationHandler() {
                private int index = -1;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        index++;
                        return index < rows.size();
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
                    if (args != null && args.length == 1 && args[0] instanceof String) {
                        Object value = rows.get(index).get(args[0]);
                        if ("getString".equals(name)) {
                            return value == null ? null : value.toString();
                        }
                        if ("getInt".equals(name)) {
                            return value instanceof Boolean
                                    ? ((Boolean) value ? 1 : 0)
                                    : ((Number) value).intValue();
                        }
                        if ("getLong".equals(name)) {
                            return ((Number) value).longValue();
                        }
                        if ("getBoolean".equals(name)) {
                            return (Boolean) value;
                        }
                    }
                    return defaultValue(method);
                }
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(
                    type.getClassLoader(),
                    new Class<?>[]{type},
                    handler);
        }

        private static Object defaultValue(Method method) {
            Class<?> type = method.getReturnType();
            if (type == Boolean.TYPE) return false;
            if (type == Integer.TYPE) return 0;
            if (type == Long.TYPE) return 0L;
            if (type == Float.TYPE) return 0F;
            if (type == Double.TYPE) return 0D;
            return null;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("unwrap not supported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
