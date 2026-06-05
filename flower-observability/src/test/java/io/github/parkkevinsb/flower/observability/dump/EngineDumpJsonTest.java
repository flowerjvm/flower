package io.github.parkkevinsb.flower.observability.dump;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.engine.EngineDump;
import io.github.parkkevinsb.flower.core.engine.EngineState;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.worker.WorkerState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class EngineDumpJsonTest {

    @Test
    void rendersEmptyEngineDump() {
        EngineDump dump = new EngineDump(EngineState.CREATED, Collections.emptyList());

        String json = EngineDumpJson.toJson(dump);

        assertThat(json).isEqualTo("{\"engineState\":\"CREATED\",\"workers\":[]}");
    }

    @Test
    void rendersWorkerWithFlow() {
        FlowSnapshot snap = new FlowSnapshot(
                FlowId.of("quay-work", "WO-1"),
                FlowState.RUNNING,
                "execute-sts",
                10,
                null);
        EngineDump.WorkerDump worker = new EngineDump.WorkerDump(
                "main", WorkerState.RUNNING, 100L, Collections.singletonList(snap));
        EngineDump dump = new EngineDump(EngineState.RUNNING, Collections.singletonList(worker));

        String json = EngineDumpJson.toJson(dump);

        assertThat(json).contains("\"engineState\":\"RUNNING\"");
        assertThat(json).contains("\"name\":\"main\"");
        assertThat(json).contains("\"intervalMillis\":100");
        assertThat(json).contains("\"flowType\":\"quay-work\"");
        assertThat(json).contains("\"flowKey\":\"WO-1\"");
        assertThat(json).contains("\"currentStepId\":\"execute-sts\"");
        assertThat(json).contains("\"currentStepNo\":10");
        assertThat(json).contains("\"executionContext\"");
        assertThat(json).contains("\"failureCause\":null");
    }

    @Test
    void rendersExecutionContext() {
        FlowSnapshot snap = new FlowSnapshot(
                FlowId.of("order", "O-1"),
                FlowState.RUNNING,
                "payment",
                1,
                null,
                ExecutionContext.builder()
                        .tenantId("tenant-a")
                        .userId("user-1")
                        .runId("run-1")
                        .traceId("trace-1")
                        .build());
        EngineDump.WorkerDump worker = new EngineDump.WorkerDump(
                "main", WorkerState.RUNNING, 100L, Collections.singletonList(snap));
        EngineDump dump = new EngineDump(EngineState.RUNNING, Collections.singletonList(worker));

        String json = EngineDumpJson.toJson(dump);

        assertThat(json).contains("\"tenantId\":\"tenant-a\"");
        assertThat(json).contains("\"userId\":\"user-1\"");
        assertThat(json).contains("\"runId\":\"run-1\"");
        assertThat(json).contains("\"traceId\":\"trace-1\"");
    }

    @Test
    void rendersFailureCauseAsString() {
        FlowSnapshot snap = new FlowSnapshot(
                FlowId.of("t", "k"),
                FlowState.FAILED,
                null,
                0,
                new IllegalStateException("boom"));
        EngineDump.WorkerDump worker = new EngineDump.WorkerDump(
                "w", WorkerState.RUNNING, 50L, Collections.singletonList(snap));
        EngineDump dump = new EngineDump(EngineState.RUNNING, Collections.singletonList(worker));

        String json = EngineDumpJson.toJson(dump);

        assertThat(json).contains("\"failureCause\":\"java.lang.IllegalStateException: boom\"");
    }

    @Test
    void escapesQuotesAndControlChars() {
        FlowSnapshot snap = new FlowSnapshot(
                FlowId.of("type-with-\"quote\"", "k\nl"),
                FlowState.READY,
                null,
                0,
                null);
        EngineDump.WorkerDump worker = new EngineDump.WorkerDump(
                "w", WorkerState.CREATED, 1L, Collections.singletonList(snap));
        EngineDump dump = new EngineDump(EngineState.CREATED, Collections.singletonList(worker));

        String json = EngineDumpJson.toJson(dump);

        assertThat(json).contains("\"flowType\":\"type-with-\\\"quote\\\"\"");
        assertThat(json).contains("\"flowKey\":\"k\\nl\"");
    }

    @Test
    void prettyJsonIsIndented() {
        EngineDump dump = new EngineDump(EngineState.CREATED, Collections.emptyList());

        String json = EngineDumpJson.toPrettyJson(dump);

        assertThat(json).contains("\n");
        assertThat(json).contains("  \"engineState\"");
    }

    @Test
    void rendersMultipleWorkersAndFlows() {
        FlowSnapshot a = new FlowSnapshot(FlowId.of("t", "a"), FlowState.RUNNING, "s", 0, null);
        FlowSnapshot b = new FlowSnapshot(FlowId.of("t", "b"), FlowState.RUNNING, "s", 0, null);
        EngineDump.WorkerDump w1 = new EngineDump.WorkerDump(
                "w1", WorkerState.RUNNING, 100L, Arrays.asList(a, b));
        EngineDump.WorkerDump w2 = new EngineDump.WorkerDump(
                "w2", WorkerState.RUNNING, 200L, Collections.emptyList());
        EngineDump dump = new EngineDump(EngineState.RUNNING, Arrays.asList(w1, w2));

        String json = EngineDumpJson.toJson(dump);

        assertThat(json).contains("\"name\":\"w1\"");
        assertThat(json).contains("\"name\":\"w2\"");
        assertThat(json).contains("\"flowKey\":\"a\"");
        assertThat(json).contains("\"flowKey\":\"b\"");
    }
}
