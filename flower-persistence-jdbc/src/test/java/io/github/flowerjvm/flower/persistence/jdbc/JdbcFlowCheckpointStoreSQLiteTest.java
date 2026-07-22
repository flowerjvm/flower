package io.github.flowerjvm.flower.persistence.jdbc;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowPersistence;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcFlowCheckpointStoreSQLiteTest {

    @TempDir
    Path tempDir;

    @Test
    void sqlite_schema_supports_checkpoint_round_trip_and_host_tables() throws Exception {
        SQLiteDataSource dataSource = sqliteDataSource("agent.db");
        applySchema(dataSource, "/flower/persistence/jdbc/sqlite/schema.sql");
        createHostTable(dataSource);

        JdbcFlowCheckpointStore store = JdbcFlowCheckpointStore.create(
                dataSource, JdbcCheckpointDialects.sqlite());
        FlowId flowId = FlowId.of("agent-run", "run-1");
        ExecutionContext executionContext = ExecutionContext.builder()
                .tenantId("tenant-a")
                .runId("run-1")
                .traceId("trace-1")
                .build();

        store.save(checkpoint(flowId, "collect-input", 10, 1_000L, executionContext));
        store.save(checkpoint(flowId, "call-model", 20, 2_000L, executionContext));

        Optional<FlowCheckpoint> found = store.find(flowId);
        assertThat(found).isPresent();
        assertThat(found.get().currentStepId()).isEqualTo("call-model");
        assertThat(found.get().currentStepNo()).isEqualTo(20);
        assertThat(found.get().currentStepEntered()).isTrue();
        assertThat(found.get().executionContext().runId()).contains("run-1");
        assertThat(store.findActive()).hasSize(1);
        assertThat(readHostValue(dataSource)).isEqualTo("remembered");

        store.delete(flowId);

        assertThat(store.find(flowId)).isEmpty();
    }

    private SQLiteDataSource sqliteDataSource(String fileName) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        String databasePath = tempDir.resolve(fileName)
                .toAbsolutePath()
                .toString()
                .replace('\\', '/');
        dataSource.setUrl("jdbc:sqlite:" + databasePath);
        return dataSource;
    }

    private static FlowCheckpoint checkpoint(
            FlowId flowId,
            String stepId,
            int stepNo,
            long updatedAtMillis,
            ExecutionContext executionContext) {
        return new FlowCheckpoint(
                flowId,
                FlowState.RUNNING,
                stepId,
                stepNo,
                true,
                FlowPersistence.DURABLE,
                "agent-worker",
                updatedAtMillis,
                "v1",
                executionContext);
    }

    private static void createHostTable(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE agent_memory (memory_key TEXT PRIMARY KEY, memory_value TEXT NOT NULL)");
            statement.execute("INSERT INTO agent_memory VALUES ('fact-1', 'remembered')");
        }
    }

    private static String readHostValue(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT memory_value FROM agent_memory WHERE memory_key = 'fact-1'")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static void applySchema(DataSource dataSource, String resourcePath) throws Exception {
        InputStream input = JdbcFlowCheckpointStoreSQLiteTest.class.getResourceAsStream(resourcePath);
        if (input == null) {
            throw new IllegalStateException("Missing schema resource: " + resourcePath);
        }
        try (Scanner scanner = new Scanner(input, "UTF-8").useDelimiter(";");
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            while (scanner.hasNext()) {
                String sql = scanner.next().trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        }
    }
}
