package io.github.flowerjvm.flower.persistence.jdbc;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowPersistence;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcFlowCheckpointStoreTest {

    @Test
    void save_binds_checkpoint_fields_in_order() {
        RecordingDataSource ds = new RecordingDataSource();
        JdbcFlowCheckpointStore store = JdbcFlowCheckpointStore.create(
                ds, JdbcCheckpointDialects.postgresql());

        store.save(new FlowCheckpoint(
                FlowId.of("order", "O-1"),
                FlowState.RUNNING,
                "payment",
                10,
                true,
                FlowPersistence.DURABLE,
                "worker-a",
                1234L,
                "v1",
                ExecutionContext.builder()
                        .tenantId("tenant-a")
                        .userId("user-1")
                        .sessionId("session-1")
                        .runId("run-1")
                        .traceId("trace-1")
                        .correlationId("corr-1")
                        .build()));

        assertThat(ds.sql).contains("ON CONFLICT");
        assertThat(ds.params).containsEntry(1, "order");
        assertThat(ds.params).containsEntry(2, "O-1");
        assertThat(ds.params).containsEntry(3, "RUNNING");
        assertThat(ds.params).containsEntry(4, "payment");
        assertThat(ds.params).containsEntry(5, 10);
        assertThat(ds.params).containsEntry(6, true);
        assertThat(ds.params).containsEntry(7, "DURABLE");
        assertThat(ds.params).containsEntry(8, "worker-a");
        assertThat(ds.params).containsEntry(9, 1234L);
        assertThat(ds.params).containsEntry(10, "v1");
        assertThat(ds.params).containsEntry(11, "tenant-a");
        assertThat(ds.params).containsEntry(12, "user-1");
        assertThat(ds.params).containsEntry(13, "session-1");
        assertThat(ds.params).containsEntry(14, "run-1");
        assertThat(ds.params).containsEntry(15, "trace-1");
        assertThat(ds.params).containsEntry(16, "corr-1");
        assertThat(ds.updateCount).isEqualTo(1);
    }

    @Test
    void find_reads_checkpoint_row() {
        RecordingDataSource ds = new RecordingDataSource();
        ds.rows.add(row("order", "O-1", "RUNNING", "payment", 20,
                true, "DURABLE", "worker-a", 4321L, "v2",
                "tenant-a", "user-1", "session-1", "run-1", "trace-1", "corr-1"));
        JdbcFlowCheckpointStore store = JdbcFlowCheckpointStore.create(
                ds, JdbcCheckpointDialects.postgresql());

        Optional<FlowCheckpoint> found = store.find(FlowId.of("order", "O-1"));

        assertThat(ds.sql).contains("WHERE flow_type = ? AND flow_key = ?");
        assertThat(ds.params).containsEntry(1, "order");
        assertThat(ds.params).containsEntry(2, "O-1");
        assertThat(found).isPresent();
        assertThat(found.get().flowId()).isEqualTo(FlowId.of("order", "O-1"));
        assertThat(found.get().state()).isEqualTo(FlowState.RUNNING);
        assertThat(found.get().currentStepId()).isEqualTo("payment");
        assertThat(found.get().currentStepNo()).isEqualTo(20);
        assertThat(found.get().currentStepEntered()).isTrue();
        assertThat(found.get().persistence()).isEqualTo(FlowPersistence.DURABLE);
        assertThat(found.get().workerName()).isEqualTo("worker-a");
        assertThat(found.get().updatedAtMillis()).isEqualTo(4321L);
        assertThat(found.get().definitionVersion()).isEqualTo("v2");
        assertThat(found.get().executionContext().tenantId()).contains("tenant-a");
        assertThat(found.get().executionContext().userId()).contains("user-1");
        assertThat(found.get().executionContext().sessionId()).contains("session-1");
        assertThat(found.get().executionContext().runId()).contains("run-1");
        assertThat(found.get().executionContext().traceId()).contains("trace-1");
        assertThat(found.get().executionContext().correlationId()).contains("corr-1");
    }

    @Test
    void findActiveByWorker_binds_worker_name() {
        RecordingDataSource ds = new RecordingDataSource();
        ds.rows.add(row("order", "O-1", "READY", null, 0,
                false, "DURABLE", "worker-a", 1L, null,
                null, null, null, null, null, null));
        JdbcFlowCheckpointStore store = JdbcFlowCheckpointStore.create(
                ds, JdbcCheckpointDialects.postgresql());

        List<FlowCheckpoint> found = store.findActiveByWorker("worker-a");

        assertThat(ds.sql).contains("worker_name = ?");
        assertThat(ds.params).containsEntry(1, "worker-a");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).state()).isEqualTo(FlowState.READY);
    }

    @Test
    void oracle_dialect_binds_boolean_as_number() {
        RecordingDataSource ds = new RecordingDataSource();
        JdbcFlowCheckpointStore store = JdbcFlowCheckpointStore.create(
                ds, JdbcCheckpointDialects.oracle());

        store.save(new FlowCheckpoint(
                FlowId.of("order", "O-1"),
                FlowState.RUNNING,
                "payment",
                10,
                true,
                FlowPersistence.DURABLE,
                "worker-a",
                1234L,
                "v1"));

        assertThat(ds.params).containsEntry(6, 1);
    }

    private static Map<String, Object> row(
            String flowType,
            String flowKey,
            String state,
            String currentStepId,
            int currentStepNo,
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
            String correlationId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("flow_type", flowType);
        row.put("flow_key", flowKey);
        row.put("state", state);
        row.put("current_step_id", currentStepId);
        row.put("current_step_no", currentStepNo);
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
        return row;
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
                            return ((Number) value).intValue();
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
