package io.github.parkkevinsb.flower.core.worker;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerSubmissionTest {

    private Worker worker;

    @BeforeEach
    void setUp() {
        worker = Worker.builder("test").build();
        Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build()
                .attach();
    }

    private static Flow stayFlow(String type, String key) {
        return Flow.builder(type, key)
                .step("only", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        return StepResult.stay();
                    }
                })
                .build();
    }

    @Test
    void reject_throws_on_duplicate() {
        worker.submit(stayFlow("t", "1"));
        assertThatThrownBy(() -> worker.submit(stayFlow("t", "1"), DuplicatePolicy.REJECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already submitted");
    }

    @Test
    void ignore_silently_drops_duplicate() {
        Flow first = stayFlow("t", "1");
        Flow second = stayFlow("t", "1");
        worker.submit(first);
        worker.submit(second, DuplicatePolicy.IGNORE);
        worker.tickOnce();

        // The second flow should never have been attached - its state stays CREATED.
        assertThat(second.state()).isEqualTo(FlowState.CREATED);
        assertThat(first.state()).isEqualTo(FlowState.RUNNING);
    }

    @Test
    void replace_cancels_existing_and_runs_new() {
        Flow first = stayFlow("t", "1");
        worker.submit(first);
        worker.tickOnce(); // first becomes RUNNING

        Flow second = stayFlow("t", "1");
        worker.submit(second, DuplicatePolicy.REPLACE);
        worker.tickOnce(); // first cancelled this tick, second attached

        worker.tickOnce(); // second runs

        assertThat(first.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(second.state()).isEqualTo(FlowState.RUNNING);
    }

    @Test
    void cancel_pending_flow_prevents_it_from_running() {
        AtomicInteger ticks = new AtomicInteger();
        Flow flow = Flow.builder("t", "1")
                .step("only", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        ticks.incrementAndGet();
                        return StepResult.stay();
                    }
                })
                .build();
        worker.submit(flow);

        boolean cancelled = worker.cancel(flow.flowId());
        worker.tickOnce();

        assertThat(cancelled).isTrue();
        assertThat(flow.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(ticks.get()).isZero();
    }

    @Test
    void replace_pending_flow_runs_replacement_only() {
        AtomicInteger firstTicks = new AtomicInteger();
        Flow first = Flow.builder("t", "1")
                .step("only", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        firstTicks.incrementAndGet();
                        return StepResult.stay();
                    }
                })
                .build();
        Flow second = stayFlow("t", "1");

        worker.submit(first);
        worker.submit(second, DuplicatePolicy.REPLACE);
        worker.tickOnce();

        assertThat(first.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(firstTicks.get()).isZero();
        assertThat(second.state()).isEqualTo(FlowState.RUNNING);
    }

    @Test
    void cancel_terminates_active_flow() {
        Flow flow = stayFlow("t", "1");
        worker.submit(flow);
        worker.tickOnce();
        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);

        boolean cancelled = worker.cancel(flow.flowId());
        assertThat(cancelled).isTrue();
        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.CANCELLED);
    }

    @Test
    void cancel_unknown_flow_returns_false() {
        assertThat(worker.cancel(io.github.parkkevinsb.flower.core.flow.FlowId.of("t", "x"))).isFalse();
    }
}
