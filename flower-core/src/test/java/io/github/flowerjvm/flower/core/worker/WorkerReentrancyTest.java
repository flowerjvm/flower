package io.github.flowerjvm.flower.core.worker;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpointStore;
import io.github.flowerjvm.flower.core.step.RecoveryPolicy;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import io.github.flowerjvm.flower.core.time.ManualClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerReentrancyTest {

    @Test
    void nested_tick_from_step_exit_is_rejected_without_corrupting_the_flow() {
        Worker worker = Worker.builder("manual").build();
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger exits = new AtomicInteger();
        AtomicReference<Throwable> nestedFailure = new AtomicReference<>();
        FlowerListener listener = new FlowerListener() {
            @Override
            public void onStepExited(FlowSnapshot flow, String stepId) {
                exits.incrementAndGet();
                captureNestedTick(worker, nestedFailure);
            }
        };
        engineFor(worker, listener, FlowCheckpointStore.NOOP).attach();
        Flow flow = Flow.builder("reentry", "exit")
                .step("only", countingStep(ticks, StepResult.done()))
                .build();

        worker.submit(flow);
        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(ticks).hasValue(1);
        assertThat(exits).hasValue(1);
        assertNestedTickRejected(nestedFailure.get());
    }

    @Test
    void nested_tick_from_step_entry_cannot_tick_a_sibling_flow_twice() {
        Worker worker = Worker.builder("manual").build();
        AtomicInteger firstTicks = new AtomicInteger();
        AtomicInteger siblingTicks = new AtomicInteger();
        AtomicReference<Throwable> nestedFailure = new AtomicReference<>();
        FlowerListener listener = new FlowerListener() {
            @Override
            public void onStepEntered(FlowSnapshot flow, String stepId) {
                if ("first".equals(flow.flowId().flowKey())) {
                    captureNestedTick(worker, nestedFailure);
                }
            }
        };
        engineFor(worker, listener, FlowCheckpointStore.NOOP).attach();
        Flow first = Flow.builder("reentry", "first")
                .step("wait", countingStep(firstTicks, StepResult.stay()))
                .build();
        Flow sibling = Flow.builder("reentry", "sibling")
                .step("wait", countingStep(siblingTicks, StepResult.stay()))
                .build();

        worker.submit(first);
        worker.submit(sibling);
        worker.tickOnce();

        assertThat(firstTicks).hasValue(1);
        assertThat(siblingTicks).hasValue(1);
        assertNestedTickRejected(nestedFailure.get());
    }

    @Test
    void nested_tick_cannot_duplicate_terminal_checkpoint_writes() {
        Worker worker = Worker.builder("manual").build();
        RecordingCheckpointStore store = new RecordingCheckpointStore();
        AtomicReference<Throwable> nestedFailure = new AtomicReference<>();
        FlowerListener listener = new FlowerListener() {
            @Override
            public void onStepExited(FlowSnapshot flow, String stepId) {
                captureNestedTick(worker, nestedFailure);
            }
        };
        engineFor(worker, listener, store).attach();
        Flow flow = Flow.builder("reentry", "durable")
                .durable()
                .durableStep("only", countingStep(new AtomicInteger(), StepResult.done()),
                        RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();

        worker.submit(flow);
        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(store.savedStates).containsExactly(FlowState.READY, FlowState.FINISHED);
        assertThat(store.deleted).containsExactly(flow.flowId());
        assertNestedTickRejected(nestedFailure.get());
    }

    private static Step countingStep(AtomicInteger ticks, StepResult result) {
        return new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                ticks.incrementAndGet();
                return result;
            }
        };
    }

    private static Engine engineFor(
            Worker worker,
            FlowerListener listener,
            FlowCheckpointStore checkpointStore) {
        return Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .checkpointStore(checkpointStore)
                .worker(worker)
                .listener(listener)
                .build();
    }

    private static void captureNestedTick(Worker worker, AtomicReference<Throwable> failure) {
        try {
            worker.tickOnce();
        } catch (Throwable t) {
            failure.compareAndSet(null, t);
        }
    }

    private static void assertNestedTickRejected(Throwable failure) {
        assertThat(failure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not allow a nested tickOnce() call");
    }

    private static final class RecordingCheckpointStore implements FlowCheckpointStore {
        private final List<FlowState> savedStates = new ArrayList<>();
        private final List<FlowId> deleted = new ArrayList<>();

        @Override
        public void save(FlowCheckpoint checkpoint) {
            savedStates.add(checkpoint.state());
        }

        @Override
        public void delete(FlowId flowId) {
            deleted.add(flowId);
        }
    }
}
