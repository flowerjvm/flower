package io.github.flowerjvm.flower.eventloop.persistence.jdbc;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowPersistence;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.eventloop.persistence.EventAwaitCheckpoint;
import io.github.flowerjvm.flower.eventloop.persistence.EventFlowCheckpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEventFlowCheckpointStoreSQLiteTest {

    @TempDir
    Path tempDir;

    @Test
    void sqlite_schema_supports_event_checkpoint_round_trip_and_tombstone() throws Exception {
        SQLiteDataSource dataSource = sqliteDataSource("agent-events.db");
        applySchema(dataSource, "/flower/eventloop/persistence/jdbc/sqlite/schema.sql");
        JdbcEventFlowCheckpointStore store = JdbcEventFlowCheckpointStore.create(
                dataSource, JdbcEventFlowCheckpointDialects.sqlite());
        FlowId flowId = FlowId.of("agent-event-run", "run-1");
        ExecutionContext executionContext = ExecutionContext.builder()
                .tenantId("tenant-a")
                .runId("run-1")
                .build();

        store.save(new EventFlowCheckpoint(
                flowId,
                FlowState.RUNNING,
                "await-tool-result",
                true,
                FlowPersistence.DURABLE,
                "agent-event-worker",
                1_000L,
                "v1",
                executionContext,
                3L,
                Arrays.asList(
                        EventAwaitCheckpoint.signal("tool-result", "call-1"),
                        EventAwaitCheckpoint.deadline(10_000L))));

        Optional<EventFlowCheckpoint> found = store.find(flowId);
        assertThat(found).isPresent();
        assertThat(found.get().currentStepId()).isEqualTo("await-tool-result");
        assertThat(found.get().currentStepEntered()).isTrue();
        assertThat(found.get().awaitGeneration()).isEqualTo(3L);
        assertThat(found.get().awaits()).hasSize(2);
        assertThat(found.get().awaits().get(0).signalKey()).isEqualTo("call-1");
        assertThat(found.get().awaits().get(1).deadlineAtMillis()).isEqualTo(10_000L);
        assertThat(store.findActive()).hasSize(1);

        store.save(new EventFlowCheckpoint(
                flowId,
                FlowState.FINISHED,
                null,
                false,
                FlowPersistence.DURABLE,
                "agent-event-worker",
                2_000L,
                "v1",
                executionContext,
                4L,
                Collections.<EventAwaitCheckpoint>emptyList()));

        assertThat(store.find(flowId)).isPresent();
        assertThat(store.findActive()).isEmpty();

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

    private static void applySchema(DataSource dataSource, String resourcePath) throws Exception {
        InputStream input = JdbcEventFlowCheckpointStoreSQLiteTest.class.getResourceAsStream(resourcePath);
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
