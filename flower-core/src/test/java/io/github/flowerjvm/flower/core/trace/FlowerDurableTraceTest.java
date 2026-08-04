package io.github.flowerjvm.flower.core.trace;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpointStore;
import io.github.flowerjvm.flower.core.step.RecoveryPolicy;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerDurableTraceTest {

    @Test
    void checkpoint_and_recovery_keep_one_logical_trace_across_runtime_segments() {
        ManualClock clock = new ManualClock(1_000L);
        MemoryStore store = new MemoryStore();
        RecordingTrace firstTrace = new RecordingTrace();
        Worker firstWorker = worker("worker-a", clock, store, firstTrace);
        Flow original = durableStayFlow();

        firstWorker.submit(original);
        firstWorker.tickOnce();

        FlowCheckpoint checkpoint = store.find(original.flowId()).orElseThrow(AssertionError::new);
        assertThat(types(firstTrace.events)).containsExactly(
                FlowerTraceEventType.FLOW_STARTED,
                FlowerTraceEventType.CHECKPOINT_SAVED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.CHECKPOINT_SAVED);
        assertThat(checkpoint.executionContext().runIdOrNull()).isNotBlank();
        assertThat(checkpoint.executionContext().traceIdOrNull())
                .isEqualTo(checkpoint.executionContext().runIdOrNull());
        String flowRunId = firstTrace.events.get(0).flowRunId();
        String firstRuntimeId = (String) firstTrace.events.get(0).attributes()
                .get(FlowerTraceAttributes.FLOW_RUNTIME_ID);
        assertThat(checkpoint.executionContext().runIdOrNull()).isEqualTo(flowRunId);
        assertThat(firstTrace.events.get(3).stepRunId())
                .isEqualTo(firstTrace.events.get(2).stepRunId());

        firstWorker.stop();
        assertThat(types(firstTrace.events)).endsWith(FlowerTraceEventType.FLOW_SUSPENDED);
        assertThat(firstTrace.events.get(firstTrace.events.size() - 1).stepRunId())
                .isEqualTo(firstTrace.events.get(2).stepRunId());

        RecordingTrace recoveredTrace = new RecordingTrace();
        Worker recoveredWorker = worker("worker-b", clock, store, recoveredTrace);
        Flow recovered = durableStayFlow().recoverFrom(checkpoint);

        recoveredWorker.submit(recovered);
        recoveredWorker.tickOnce();

        assertThat(types(recoveredTrace.events)).containsExactly(
                FlowerTraceEventType.FLOW_RECOVERED,
                FlowerTraceEventType.CHECKPOINT_SAVED,
                FlowerTraceEventType.STEP_STARTED);
        assertThat(recoveredTrace.events).allSatisfy(event -> {
            assertThat(event.flowRunId()).isEqualTo(flowRunId);
            assertThat(event.traceId()).isEqualTo(checkpoint.executionContext().traceIdOrNull());
        });
        String recoveredRuntimeId = (String) recoveredTrace.events.get(0).attributes()
                .get(FlowerTraceAttributes.FLOW_RUNTIME_ID);
        assertThat(recoveredRuntimeId).isNotEqualTo(firstRuntimeId);
        assertThat(recoveredTrace.events.get(0).attributes())
                .containsEntry(FlowerTraceAttributes.FLOW_RECOVERED, true)
                .containsEntry(FlowerTraceAttributes.FLOW_RECOVERY_WORKER, "worker-a");
        assertThat(recoveredTrace.events.get(2).attributes())
                .containsEntry(FlowerTraceAttributes.STEP_RECOVERED, true);
    }

    private static Worker worker(
            String name,
            ManualClock clock,
            FlowCheckpointStore store,
            FlowerTraceListener trace) {
        Worker worker = Worker.builder(name).build();
        Engine engine = Engine.builder()
                .clock(clock)
                .eventBus(InMemoryEventBus.create())
                .checkpointStore(store)
                .worker(worker)
                .listener(trace)
                .build();
        engine.attach();
        return worker;
    }

    private static Flow durableStayFlow() {
        return Flow.builder("durable-trace", "flow-1")
                .durable()
                .definitionVersion("v1")
                .durableStep("wait", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        return StepResult.stay();
                    }
                }, RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();
    }

    private static List<FlowerTraceEventType> types(List<FlowerTraceEvent> events) {
        List<FlowerTraceEventType> types = new ArrayList<>();
        for (FlowerTraceEvent event : events) {
            types.add(event.type());
        }
        return types;
    }

    private static final class RecordingTrace implements FlowerTraceListener {
        private final List<FlowerTraceEvent> events = new ArrayList<>();

        @Override
        public void onTraceEvent(FlowerTraceEvent event) {
            events.add(event);
        }
    }

    private static final class MemoryStore implements FlowCheckpointStore {
        private final Map<FlowId, FlowCheckpoint> checkpoints = new LinkedHashMap<>();

        @Override
        public void save(FlowCheckpoint checkpoint) {
            checkpoints.put(checkpoint.flowId(), checkpoint);
        }

        @Override
        public void delete(FlowId flowId) {
            checkpoints.remove(flowId);
        }

        @Override
        public Optional<FlowCheckpoint> find(FlowId flowId) {
            return Optional.ofNullable(checkpoints.get(flowId));
        }
    }
}
