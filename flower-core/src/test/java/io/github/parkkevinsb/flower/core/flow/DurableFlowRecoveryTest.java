package io.github.parkkevinsb.flower.core.flow;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpoint;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpointStore;
import io.github.parkkevinsb.flower.core.step.DurableStep;
import io.github.parkkevinsb.flower.core.step.RecoveryPolicy;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurableFlowRecoveryTest {

    private ManualClock clock;
    private InMemoryCheckpointStore store;
    private Worker worker;
    private Engine engine;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000);
        store = new InMemoryCheckpointStore();
        worker = Worker.builder("test").build();
        engine = Engine.builder()
                .clock(clock)
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .checkpointStore(store)
                .build();
        engine.attach();
    }

    @Test
    void transient_flow_is_default_and_needs_no_recovery_policy() {
        Flow flow = Flow.builder("t", "1")
                .step("only", stayStep())
                .build();

        assertThat(flow.persistence()).isEqualTo(FlowPersistence.TRANSIENT);
    }

    @Test
    void durable_flow_rejects_step_without_recovery_policy() {
        assertThatThrownBy(() -> Flow.builder("t", "1")
                .durable()
                .step("only", stayStep())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recovery policy");
    }

    @Test
    void durable_flow_saves_latest_position_after_tick() {
        Step step = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                ctx.setStepNo(7);
                return StepResult.stay();
            }
        };
        Flow flow = Flow.builder("t", "1")
                .durable()
                .durableStep("only", step, RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();

        worker.submit(flow);
        worker.tickOnce();

        FlowCheckpoint checkpoint = store.get(flow.flowId());
        assertThat(checkpoint).isNotNull();
        assertThat(checkpoint.persistence()).isEqualTo(FlowPersistence.DURABLE);
        assertThat(checkpoint.state()).isEqualTo(FlowState.RUNNING);
        assertThat(checkpoint.currentStepId()).isEqualTo("only");
        assertThat(checkpoint.currentStepNo()).isEqualTo(7);
        assertThat(checkpoint.currentStepEntered()).isTrue();
        assertThat(checkpoint.workerName()).isEqualTo("test");
    }

    @Test
    void checkpoint_keeps_definition_version_and_rejects_mismatch() {
        Flow flow = Flow.builder("t", "1")
                .durable()
                .definitionVersion("v1")
                .durableStep("only", stayStep(), RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();
        worker.submit(flow);
        worker.tickOnce();

        FlowCheckpoint checkpoint = store.get(flow.flowId());
        assertThat(checkpoint.definitionVersion()).isEqualTo("v1");

        Flow changed = Flow.builder("t", "1")
                .durable()
                .definitionVersion("v2")
                .durableStep("only", stayStep(), RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();
        assertThatThrownBy(() -> changed.recoverFrom(checkpoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definitionVersion mismatch");
    }

    @Test
    void durable_flow_deletes_checkpoint_on_terminal_state() {
        Flow flow = Flow.builder("t", "1")
                .durable()
                .durableStep("only", doneStep(), RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();

        worker.submit(flow);
        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(store.get(flow.flowId())).isNull();
        assertThat(store.deletes).containsExactly(flow.flowId());
    }

    @Test
    void recovered_reenter_step_resumes_with_saved_stepNo() {
        Step firstRun = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                ctx.setStepNo(10);
                return StepResult.stay();
            }
        };
        Flow original = Flow.builder("t", "1")
                .durable()
                .durableStep("only", firstRun, RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();
        worker.submit(original);
        worker.tickOnce();
        FlowCheckpoint checkpoint = store.get(original.flowId());

        List<String> trace = new ArrayList<>();
        Step recoveredStep = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                trace.add("enter");
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                trace.add("tick:" + ctx.stepNo());
                return StepResult.done();
            }
        };
        Worker recoveredWorker = Worker.builder("recovered").build();
        Engine.builder()
                .clock(clock)
                .eventBus(InMemoryEventBus.create())
                .worker(recoveredWorker)
                .checkpointStore(store)
                .build()
                .attach();
        Flow recovered = Flow.builder("t", "1")
                .durable()
                .durableStep("only", recoveredStep, RecoveryPolicy.REENTER_IDEMPOTENT)
                .build()
                .recoverFrom(checkpoint);

        recoveredWorker.submit(recovered);
        recoveredWorker.tickOnce();

        assertThat(recovered.state()).isEqualTo(FlowState.FINISHED);
        assertThat(trace).containsExactly("enter", "tick:10");
    }

    @Test
    void resume_only_step_uses_onResume_instead_of_onEnter() {
        FlowCheckpoint checkpoint = new FlowCheckpoint(
                FlowId.of("t", "1"),
                FlowState.RUNNING,
                "only",
                5,
                true,
                FlowPersistence.DURABLE,
                "test",
                1_000);
        List<String> trace = new ArrayList<>();
        Flow recovered = Flow.builder("t", "1")
                .durable()
                .step("only", new ResumeOnlyStep(trace))
                .build()
                .recoverFrom(checkpoint);

        worker.submit(recovered);
        worker.tickOnce();

        assertThat(recovered.state()).isEqualTo(FlowState.FINISHED);
        assertThat(trace).containsExactly("resume:5", "tick:5");
    }

    @Test
    void stop_preserves_durable_flow_but_still_cancels_transient_flow() {
        Flow durable = Flow.builder("t", "durable")
                .durable()
                .durableStep("only", stayStep(), RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();
        Flow transientFlow = Flow.builder("t", "transient")
                .step("only", stayStep())
                .build();

        worker.submit(durable);
        worker.submit(transientFlow);
        worker.tickOnce();
        engine.stop();

        assertThat(durable.state()).isEqualTo(FlowState.RUNNING);
        assertThat(transientFlow.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(store.get(durable.flowId())).isNotNull();
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

    private static final class ResumeOnlyStep extends DurableStep {
        private final List<String> trace;

        private ResumeOnlyStep(List<String> trace) {
            super(RecoveryPolicy.RESUME_ONLY);
            this.trace = trace;
        }

        @Override
        protected void onEnter(StepContext ctx) {
            trace.add("enter");
        }

        @Override
        protected void onResume(StepContext ctx) {
            trace.add("resume:" + ctx.stepNo());
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            trace.add("tick:" + ctx.stepNo());
            return StepResult.done();
        }
    }

    private static final class InMemoryCheckpointStore implements FlowCheckpointStore {
        private final Map<FlowId, FlowCheckpoint> checkpoints = new LinkedHashMap<>();
        private final List<FlowId> deletes = new ArrayList<>();

        @Override
        public void save(FlowCheckpoint checkpoint) {
            checkpoints.put(checkpoint.flowId(), checkpoint);
        }

        @Override
        public void delete(FlowId flowId) {
            checkpoints.remove(flowId);
            deletes.add(flowId);
        }

        @Override
        public Optional<FlowCheckpoint> find(FlowId flowId) {
            return Optional.ofNullable(checkpoints.get(flowId));
        }

        @Override
        public List<FlowCheckpoint> findActive() {
            return new ArrayList<>(checkpoints.values());
        }

        @Override
        public List<FlowCheckpoint> findActiveByWorker(String workerName) {
            return checkpoints.values().stream()
                    .filter(c -> workerName == null
                            ? c.workerName() == null
                            : workerName.equals(c.workerName()))
                    .collect(Collectors.toList());
        }

        private FlowCheckpoint get(FlowId flowId) {
            return checkpoints.get(flowId);
        }
    }
}
