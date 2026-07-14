package io.github.flowerjvm.flower.core.flow;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpointStore;
import io.github.flowerjvm.flower.core.step.RecoveryPolicy;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import io.github.flowerjvm.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FlowExecutionContextTest {

    @Test
    void flow_defaults_to_empty_execution_context() {
        Flow flow = Flow.builder("order", "O-1")
                .step("only", doneStep())
                .build();

        assertThat(flow.executionContext()).isSameAs(ExecutionContext.empty());
        assertThat(flow.snapshot().executionContext()).isSameAs(ExecutionContext.empty());
    }

    @Test
    void step_context_exposes_flow_execution_context() {
        ExecutionContext execution = ExecutionContext.builder()
                .tenantId("tenant-a")
                .userId("user-1")
                .runId("run-1")
                .traceId("trace-1")
                .build();
        AtomicReference<ExecutionContext> seen = new AtomicReference<>();
        Step step = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                seen.set(ctx.executionContext());
                return StepResult.done();
            }
        };
        Worker worker = Worker.builder("test").build();
        Engine.builder()
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build()
                .attach();

        Flow flow = Flow.builder("order", "O-1")
                .executionContext(execution)
                .step("only", step)
                .build();
        worker.submit(flow);
        worker.tickOnce();

        assertThat(seen.get()).isEqualTo(execution);
        assertThat(flow.snapshot().executionContext()).isEqualTo(execution);
    }

    @Test
    void durable_checkpoint_and_recovery_keep_execution_context() {
        ExecutionContext execution = ExecutionContext.builder()
                .tenantId("tenant-a")
                .userId("user-1")
                .runId("run-1")
                .traceId("trace-1")
                .build();
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        Worker worker = Worker.builder("test").build();
        Engine.builder()
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .checkpointStore(store)
                .build()
                .attach();
        Flow original = Flow.builder("order", "O-1")
                .executionContext(execution)
                .durable()
                .durableStep("only", stayStep(), RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();
        worker.submit(original);
        worker.tickOnce();

        FlowCheckpoint checkpoint = store.find(original.flowId()).get();
        Flow recovered = Flow.builder("order", "O-1")
                .durable()
                .durableStep("only", stayStep(), RecoveryPolicy.REENTER_IDEMPOTENT)
                .build()
                .recoverFrom(checkpoint);

        assertThat(checkpoint.executionContext()).isEqualTo(execution);
        assertThat(recovered.executionContext()).isEqualTo(execution);
    }

    private static Step stayStep() {
        return new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.stay();
            }
        };
    }

    private static Step doneStep() {
        return new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.done();
            }
        };
    }

    private static final class InMemoryCheckpointStore implements FlowCheckpointStore {
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
