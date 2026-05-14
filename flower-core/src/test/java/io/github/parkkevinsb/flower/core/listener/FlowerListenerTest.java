package io.github.parkkevinsb.flower.core.listener;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerListenerTest {

    @Test
    void listener_sees_full_flow_lifecycle() {
        List<String> events = new ArrayList<>();
        FlowerListener l = new FlowerListener() {
            @Override public void onFlowSubmitted(FlowSnapshot f) { events.add("submitted:" + f.flowId()); }
            @Override public void onStepEntered(FlowSnapshot f, String s) { events.add("entered:" + s); }
            @Override public void onStepExited(FlowSnapshot f, String s) { events.add("exited:" + s); }
            @Override public void onFlowFinished(FlowSnapshot f) { events.add("finished:" + f.flowId()); }
            @Override public void onFlowFailed(FlowSnapshot f, Throwable t) { events.add("failed:" + f.flowId()); }
            @Override public void onFlowCancelled(FlowSnapshot f) { events.add("cancelled:" + f.flowId()); }
        };

        Worker worker = Worker.builder("test").build();
        Engine engine = Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .listener(l)
                .build();
        engine.attach();

        Step a = new Step() {
            @Override protected StepResult onTick(StepContext ctx) { return StepResult.advance(); }
        };
        Step b = new Step() {
            @Override protected StepResult onTick(StepContext ctx) { return StepResult.done(); }
        };
        Flow flow = Flow.builder("t", "1").step("a", a).step("b", b).build();
        worker.submit(flow);

        worker.tickOnce(); // entered a, exited a (advance)
        worker.tickOnce(); // entered b, exited b (done) -> finished

        assertThat(events).containsExactly(
                "submitted:t/1",
                "entered:a", "exited:a",
                "entered:b", "exited:b", "finished:t/1");
    }

    @Test
    void listener_sees_failure() {
        List<String> events = new ArrayList<>();
        FlowerListener l = new FlowerListener() {
            @Override public void onFlowFailed(FlowSnapshot f, Throwable cause) {
                events.add("failed:" + cause.getMessage());
            }
        };
        Worker worker = Worker.builder("test").build();
        Engine engine = Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .listener(l)
                .build();
        engine.attach();

        Step bad = new Step() {
            @Override protected StepResult onTick(StepContext ctx) {
                return StepResult.fail(new RuntimeException("nope"));
            }
        };
        Flow flow = Flow.builder("t", "1").step("a", bad).build();
        worker.submit(flow);
        worker.tickOnce();

        assertThat(events).containsExactly("failed:nope");
    }

    @Test
    void listener_sees_active_flow_cancellation() {
        List<String> events = new ArrayList<>();
        FlowerListener l = new FlowerListener() {
            @Override public void onStepExited(FlowSnapshot f, String stepId) {
                events.add("exited:" + stepId);
            }

            @Override public void onFlowCancelled(FlowSnapshot f) {
                events.add("cancelled:" + f.flowId());
            }
        };
        Worker worker = Worker.builder("test").build();
        Engine engine = Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .listener(l)
                .build();
        engine.attach();

        Step stay = new Step() {
            @Override protected StepResult onTick(StepContext ctx) { return StepResult.stay(); }
        };
        Flow flow = Flow.builder("t", "1").step("a", stay).build();
        worker.submit(flow);

        worker.tickOnce();
        worker.cancel(flow.flowId());
        worker.tickOnce();

        assertThat(events).containsExactly("exited:a", "cancelled:t/1");
    }

    @Test
    void listener_exception_does_not_break_tick() {
        FlowerListener bad = new FlowerListener() {
            @Override public void onFlowFinished(FlowSnapshot f) {
                throw new RuntimeException("boom");
            }
        };
        Worker worker = Worker.builder("test").build();
        Engine engine = Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .listener(bad)
                .build();
        engine.attach();

        Step done = new Step() {
            @Override protected StepResult onTick(StepContext ctx) { return StepResult.done(); }
        };
        Flow flow = Flow.builder("t", "1").step("a", done).build();
        worker.submit(flow);

        // Should not throw despite listener exception.
        worker.tickOnce();
    }

    @Test
    void listener_exception_is_reported_to_error_hook() {
        List<String> errors = new ArrayList<>();
        FlowerListener bad = new FlowerListener() {
            @Override public void onFlowFinished(FlowSnapshot f) {
                throw new RuntimeException("boom");
            }

            @Override public void onListenerError(FlowSnapshot f, String callbackName, Throwable cause) {
                errors.add(callbackName + ":" + cause.getMessage() + ":" + f.flowId());
            }
        };
        Worker worker = Worker.builder("test").build();
        Engine engine = Engine.builder()
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .listener(bad)
                .build();
        engine.attach();

        Step done = new Step() {
            @Override protected StepResult onTick(StepContext ctx) { return StepResult.done(); }
        };
        Flow flow = Flow.builder("t", "1").step("a", done).build();
        worker.submit(flow);

        worker.tickOnce();

        assertThat(errors).containsExactly("onFlowFinished:boom:t/1");
    }
}
