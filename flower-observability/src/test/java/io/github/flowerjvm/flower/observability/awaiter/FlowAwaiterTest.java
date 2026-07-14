package io.github.flowerjvm.flower.observability.awaiter;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowAwaiterTest {

    @Test
    void awaitTerminal_returns_finished_snapshot() throws Exception {
        FlowAwaiter awaiter = FlowAwaiter.create();
        Worker worker = Worker.builder("test").build();
        Engine engine = Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .listener(awaiter)
                .build();
        engine.attach();

        Step done = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.done();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", done).build();
        worker.submit(flow);

        worker.tickOnce();
        FlowSnapshot snapshot = awaiter.awaitTerminal(flow, 10L);

        assertThat(snapshot.flowId()).isEqualTo(flow.flowId());
        assertThat(snapshot.state()).isEqualTo(FlowState.FINISHED);
        assertThat(snapshot.currentStepId()).isNull();
    }

    @Test
    void awaitTerminal_returns_failed_snapshot_with_cause() throws Exception {
        FlowAwaiter awaiter = FlowAwaiter.create();
        Worker worker = Worker.builder("test").build();
        Engine engine = Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .listener(awaiter)
                .build();
        engine.attach();

        Step fail = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.fail(new RuntimeException("nope"));
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", fail).build();
        worker.submit(flow);

        worker.tickOnce();
        FlowSnapshot snapshot = awaiter.awaitTerminal("test", "1", 10L);

        assertThat(snapshot.state()).isEqualTo(FlowState.FAILED);
        assertThat(snapshot.failureCause()).hasMessage("nope");
    }

    @Test
    void awaitTerminal_times_out_when_flow_is_still_running() {
        FlowAwaiter awaiter = FlowAwaiter.create();
        Worker worker = Worker.builder("test").build();
        Engine engine = Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .listener(awaiter)
                .build();
        engine.attach();

        Step stay = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.stay();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", stay).build();
        worker.submit(flow);

        worker.tickOnce();

        assertThatThrownBy(() -> awaiter.awaitTerminal(flow, 1L))
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("test/1");
    }
}
