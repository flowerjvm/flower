package io.github.flowerjvm.flower.core.worker;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import io.github.flowerjvm.flower.core.time.ManualClock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerOwnershipTest {

    @Test
    void same_flow_id_is_rejected_across_workers_in_one_engine() {
        Worker a = Worker.builder("a").build();
        Worker b = Worker.builder("b").build();
        Engine engine = engineWith(a, b);
        engine.attach();

        Flow first = stayFlow("order", "42");
        Flow second = stayFlow("order", "42");

        a.submit(first);

        assertThatThrownBy(() -> b.submit(second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already owned by worker a");
        assertThat(second.state()).isEqualTo(FlowState.CREATED);
    }

    @Test
    void duplicate_ignore_silently_drops_cross_worker_submission() {
        Worker a = Worker.builder("a").build();
        Worker b = Worker.builder("b").build();
        Engine engine = engineWith(a, b);
        engine.attach();

        Flow first = stayFlow("order", "42");
        Flow second = stayFlow("order", "42");

        a.submit(first);
        b.submit(second, DuplicatePolicy.IGNORE);

        assertThat(second.state()).isEqualTo(FlowState.CREATED);
    }

    @Test
    void replace_does_not_cross_worker_ownership_boundary() {
        Worker a = Worker.builder("a").build();
        Worker b = Worker.builder("b").build();
        Engine engine = engineWith(a, b);
        engine.attach();

        a.submit(stayFlow("order", "42"));

        assertThatThrownBy(() -> b.submit(stayFlow("order", "42"), DuplicatePolicy.REPLACE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already owned by worker a");
    }

    @Test
    void ownership_is_released_after_terminal_flow() {
        Worker a = Worker.builder("a").build();
        Worker b = Worker.builder("b").build();
        Engine engine = engineWith(a, b);
        engine.attach();

        Flow first = doneFlow("order", "42");
        Flow second = stayFlow("order", "42");

        a.submit(first);
        a.tickOnce();

        assertThat(first.state()).isEqualTo(FlowState.FINISHED);

        b.submit(second);
        b.tickOnce();

        assertThat(second.state()).isEqualTo(FlowState.RUNNING);
    }

    @Test
    void engine_cancel_routes_to_current_owner() {
        Worker a = Worker.builder("a").build();
        Worker b = Worker.builder("b").build();
        Engine engine = engineWith(a, b);
        engine.attach();
        FlowId id = FlowId.of("order", "42");
        Flow flow = stayFlow("order", "42");

        engine.submit("b", flow);
        assertThat(engine.cancel(id)).isTrue();
        b.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(engine.cancel(id)).isFalse();
    }

    @Test
    void ownership_is_released_when_pending_flow_is_cancelled_before_tick() {
        Worker a = Worker.builder("a").build();
        Worker b = Worker.builder("b").build();
        Engine engine = engineWith(a, b);
        engine.attach();
        FlowId id = FlowId.of("order", "42");
        Flow first = stayFlow("order", "42");
        Flow second = stayFlow("order", "42");

        engine.submit("a", first);
        assertThat(engine.cancel(id)).isTrue();

        b.submit(second);
        b.tickOnce();

        assertThat(first.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(second.state()).isEqualTo(FlowState.RUNNING);
    }

    private static Engine engineWith(Worker a, Worker b) {
        return Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(a)
                .worker(b)
                .build();
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

    private static Flow doneFlow(String type, String key) {
        return Flow.builder(type, key)
                .step("only", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        return StepResult.done();
                    }
                })
                .build();
    }
}
