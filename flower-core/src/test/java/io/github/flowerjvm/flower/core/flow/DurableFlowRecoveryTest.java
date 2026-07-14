package io.github.flowerjvm.flower.core.flow;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpointStore;
import io.github.flowerjvm.flower.core.step.DurableStep;
import io.github.flowerjvm.flower.core.step.RecoveryPolicy;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
    void durable_flow_skips_checkpoint_when_position_is_unchanged() {
        Flow flow = Flow.builder("t", "1")
                .durable()
                .durableStep("only", stayStep(), RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();

        worker.submit(flow);
        worker.tickOnce();
        assertThat(store.saveCalls).isEqualTo(2);

        clock.advance(1_000L);
        worker.tickOnce();

        assertThat(store.saveCalls).isEqualTo(2);
    }

    @Test
    void durable_flow_saves_checkpoint_when_stepNo_changes_on_stay() {
        final AtomicInteger ticks = new AtomicInteger();
        Flow flow = Flow.builder("t", "1")
                .durable()
                .durableStep("only", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        ctx.setStepNo(ticks.incrementAndGet());
                        return StepResult.stay();
                    }
                }, RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();

        worker.submit(flow);
        worker.tickOnce();
        assertThat(store.saveCalls).isEqualTo(2);

        worker.tickOnce();

        FlowCheckpoint checkpoint = store.get(flow.flowId());
        assertThat(store.saveCalls).isEqualTo(3);
        assertThat(checkpoint.currentStepNo()).isEqualTo(2);
    }

    @Test
    void durable_flow_rejects_runtime_timeout_helper() {
        Flow flow = Flow.builder("t", "1")
                .durable()
                .durableStep("only", new Step() {
                    @Override
                    protected void onEnter(StepContext ctx) {
                        ctx.startTimeout(5_000);
                    }

                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        return StepResult.stay();
                    }
                }, RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();

        worker.submit(flow);
        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.failureCause())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StepContext.startTimeout is runtime-only")
                .hasMessageContaining("durable Flow");
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

    @Test
    void checkpoint_save_failure_halts_durable_flow_before_next_tick() {
        AtomicInteger secondTicks = new AtomicInteger();
        store.failSaveOnCall(2, new RuntimeException("save down"));
        Flow flow = Flow.builder("t", "1")
                .durable()
                .durableStep("first", doneStep(), RecoveryPolicy.REENTER_IDEMPOTENT)
                .durableStep("second", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        secondTicks.incrementAndGet();
                        return StepResult.done();
                    }
                }, RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();

        worker.submit(flow);
        worker.tickOnce();
        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.CHECKPOINT_FAILED);
        assertThat(flow.failureCause()).hasMessage("save down");
        assertThat(secondTicks.get()).isZero();
        assertThat(worker.snapshot()).isEmpty();
    }

    @Test
    void terminal_checkpoint_delete_failure_keeps_tombstone_out_of_recovery() {
        store.failDeleteOnCall(1, new RuntimeException("delete down"));
        Flow flow = Flow.builder("t", "1")
                .durable()
                .durableStep("only", doneStep(), RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();

        worker.submit(flow);
        worker.tickOnce();

        FlowCheckpoint tombstone = store.get(flow.flowId());
        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(tombstone).isNotNull();
        assertThat(tombstone.state()).isEqualTo(FlowState.FINISHED);
        assertThat(store.findActive()).isEmpty();
        assertThat(worker.snapshot()).isEmpty();
    }

    @Test
    void terminal_checkpoint_save_failure_halts_before_finished_event() {
        InMemoryCheckpointStore failingStore = new InMemoryCheckpointStore();
        failingStore.failSaveOnCall(2, new RuntimeException("terminal save down"));
        Worker localWorker = Worker.builder("local").build();
        List<String> events = new ArrayList<>();
        Engine.builder()
                .clock(clock)
                .eventBus(InMemoryEventBus.create())
                .worker(localWorker)
                .checkpointStore(failingStore)
                .listener(new FlowerListener() {
                    @Override
                    public void onFlowFinished(FlowSnapshot flow) {
                        events.add("finished");
                    }

                    @Override
                    public void onFlowFailed(FlowSnapshot flow, Throwable cause) {
                        events.add("failed:" + flow.state() + ":" + cause.getMessage());
                    }
                })
                .build()
                .attach();
        Flow flow = Flow.builder("t", "terminal-save")
                .durable()
                .durableStep("only", doneStep(), RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();

        localWorker.submit(flow);
        localWorker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.CHECKPOINT_FAILED);
        assertThat(events).containsExactly("failed:CHECKPOINT_FAILED:terminal save down");
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
        private int saveCalls;
        private int deleteCalls;
        private int failSaveOnCall = -1;
        private int failDeleteOnCall = -1;
        private RuntimeException saveFailure;
        private RuntimeException deleteFailure;

        @Override
        public void save(FlowCheckpoint checkpoint) {
            saveCalls++;
            if (saveCalls == failSaveOnCall) {
                throw saveFailure;
            }
            checkpoints.put(checkpoint.flowId(), checkpoint);
        }

        @Override
        public void delete(FlowId flowId) {
            deleteCalls++;
            if (deleteCalls == failDeleteOnCall) {
                throw deleteFailure;
            }
            checkpoints.remove(flowId);
            deletes.add(flowId);
        }

        @Override
        public Optional<FlowCheckpoint> find(FlowId flowId) {
            return Optional.ofNullable(checkpoints.get(flowId));
        }

        @Override
        public List<FlowCheckpoint> findActive() {
            return checkpoints.values().stream()
                    .filter(c -> c.state() == FlowState.READY || c.state() == FlowState.RUNNING)
                    .collect(Collectors.toList());
        }

        @Override
        public List<FlowCheckpoint> findActiveByWorker(String workerName) {
            return checkpoints.values().stream()
                    .filter(c -> c.state() == FlowState.READY || c.state() == FlowState.RUNNING)
                    .filter(c -> workerName == null
                            ? c.workerName() == null
                            : workerName.equals(c.workerName()))
                    .collect(Collectors.toList());
        }

        private FlowCheckpoint get(FlowId flowId) {
            return checkpoints.get(flowId);
        }

        private void failSaveOnCall(int callNumber, RuntimeException failure) {
            failSaveOnCall = callNumber;
            saveFailure = failure;
        }

        private void failDeleteOnCall(int callNumber, RuntimeException failure) {
            failDeleteOnCall = callNumber;
            deleteFailure = failure;
        }
    }
}
