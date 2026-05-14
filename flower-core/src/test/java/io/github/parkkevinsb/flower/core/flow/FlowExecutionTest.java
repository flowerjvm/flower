package io.github.parkkevinsb.flower.core.flow;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.step.GuardResult;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.support.TestSteps;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class FlowExecutionTest {

    private ManualClock clock;
    private Worker worker;
    private Engine engine;

    @BeforeEach
    void setUp() {
        clock = new ManualClock();
        worker = Worker.builder("test").build();
        engine = Engine.builder()
                .clock(clock)
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .build();
        engine.attach();
    }

    @Test
    void flow_advances_through_steps_in_order() {
        List<String> trace = new ArrayList<>();
        Flow flow = Flow.builder("test", "1")
                .step("a", new TestSteps.RecordingStep("a", trace, StepResult.advance()))
                .step("b", new TestSteps.RecordingStep("b", trace, StepResult.advance()))
                .step("c", new TestSteps.RecordingStep("c", trace, StepResult.advance()))
                .build();
        worker.submit(flow);

        // each tick: enter+tick on current. advance -> exit. next tick enters next.
        worker.tickOnce(); // enter a, tick a -> advance -> exit a, currentIndex=1
        worker.tickOnce(); // enter b, tick b -> advance -> exit b, currentIndex=2
        worker.tickOnce(); // enter c, tick c -> advance -> exit c, FINISHED

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(trace).containsExactly(
                "enter:a", "tick:a", "exit:a",
                "enter:b", "tick:b", "exit:b",
                "enter:c", "tick:c", "exit:c");
    }

    @Test
    void stay_keeps_step_until_advance() {
        TestSteps.CountThenAdvanceStep step = new TestSteps.CountThenAdvanceStep(3);
        Flow flow = Flow.builder("test", "1")
                .step("only", step)
                .build();
        worker.submit(flow);

        worker.tickOnce();
        worker.tickOnce();
        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);
        worker.tickOnce(); // 3rd tick -> advance -> FINISHED
        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
    }

    @Test
    void stepNo_drives_internal_cursor() {
        TestSteps.StepNoCursorStep step = new TestSteps.StepNoCursorStep();
        Flow flow = Flow.builder("test", "1").step("only", step).build();
        worker.submit(flow);

        // Three onTick calls walk stepNo 0 -> 10 -> 20 -> advance
        worker.tickOnce();
        worker.tickOnce();
        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(step.trace).containsExactly(0, 10, 20);
    }

    @Test
    void repeat_resets_step_state_and_re_enters() {
        List<String> trace = new ArrayList<>();
        // Step that emits ADVANCE on the second entry.
        Step step = new Step() {
            int entries;

            @Override
            protected void onEnter(StepContext ctx) {
                entries++;
                trace.add("enter#" + entries);
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                trace.add("tick#" + entries + "/no=" + ctx.stepNo());
                if (entries == 1) {
                    return StepResult.repeat();
                }
                return StepResult.advance();
            }

            @Override
            protected void onReset(StepContext ctx) {
                trace.add("reset");
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", step).build();
        worker.submit(flow);

        worker.tickOnce(); // enter#1, tick -> repeat -> reset
        worker.tickOnce(); // enter#2, tick -> advance -> FINISHED

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(trace).containsExactly(
                "enter#1", "tick#1/no=0", "reset",
                "enter#2", "tick#2/no=0");
    }

    @Test
    void goTo_jumps_to_step_id() {
        List<String> trace = new ArrayList<>();
        Step a = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                trace.add("a");
                return StepResult.goTo("c");
            }
        };
        Step b = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                trace.add("b");
                return StepResult.advance();
            }
        };
        Step c = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                trace.add("c");
                return StepResult.done();
            }
        };
        Flow flow = Flow.builder("test", "1")
                .step("a", a).step("b", b).step("c", c).build();
        worker.submit(flow);

        worker.tickOnce(); // enter a, tick -> goTo c -> exit a, currentIndex=2
        worker.tickOnce(); // enter c, tick -> done

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(trace).containsExactly("a", "c");
    }

    @Test
    void goTo_with_unknown_id_fails_flow() {
        Step a = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.goTo("missing");
            }
        };
        Flow flow = Flow.builder("test", "1").step("a", a).build();
        worker.submit(flow);

        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.failureCause()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void guard_hold_prevents_step_lifecycle_until_passes() {
        AtomicBoolean open = new AtomicBoolean(false);
        List<String> trace = new ArrayList<>();
        Step step = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                trace.add("enter");
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                trace.add("tick");
                return StepResult.done();
            }
        };
        Flow flow = Flow.builder("test", "1")
                .step("guarded", step, ctx -> open.get()
                        ? GuardResult.pass()
                        : GuardResult.hold())
                .build();
        worker.submit(flow);

        worker.tickOnce();
        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);
        assertThat(trace).isEmpty();

        open.set(true);
        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(trace).containsExactly("enter", "tick");
    }

    @Test
    void guard_goTo_skips_guarded_step() {
        List<String> trace = new ArrayList<>();
        Step skipped = new TestSteps.RecordingStep("skipped", trace, StepResult.done());
        Step target = new TestSteps.RecordingStep("target", trace, StepResult.done());
        Flow flow = Flow.builder("test", "1")
                .step("a", skipped, ctx -> GuardResult.goTo("b"))
                .step("b", target)
                .build();
        worker.submit(flow);

        worker.tickOnce();
        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(trace).containsExactly("enter:target", "tick:target", "exit:target");
    }

    @Test
    void guard_fail_fails_flow_without_entering_step() {
        List<String> trace = new ArrayList<>();
        Step skipped = new TestSteps.RecordingStep("skipped", trace, StepResult.done());
        Flow flow = Flow.builder("test", "1")
                .step("a", skipped, ctx -> GuardResult.fail(new RuntimeException("guard-nope")))
                .build();
        worker.submit(flow);

        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.failureCause()).hasMessage("guard-nope");
        assertThat(trace).isEmpty();
    }

    @Test
    void worker_ticks_multiple_flows_in_submission_order() {
        List<String> trace = new ArrayList<>();
        Flow f1 = Flow.builder("test", "1")
                .step("only", new TestSteps.RecordingStep("f1", trace, StepResult.done()))
                .build();
        Flow f2 = Flow.builder("test", "2")
                .step("only", new TestSteps.RecordingStep("f2", trace, StepResult.done()))
                .build();
        worker.submit(f1);
        worker.submit(f2);

        worker.tickOnce();

        assertThat(trace).containsExactly(
                "enter:f1", "tick:f1", "exit:f1",
                "enter:f2", "tick:f2", "exit:f2");
        assertThat(f1.state()).isEqualTo(FlowState.FINISHED);
        assertThat(f2.state()).isEqualTo(FlowState.FINISHED);
    }

    @Test
    void step_throwing_in_onTick_fails_flow() {
        Step bad = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                throw new RuntimeException("boom");
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", bad).build();
        worker.submit(flow);

        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.failureCause()).hasMessage("boom");
    }

    @Test
    void step_throwing_in_onEnter_fails_flow_with_original_cause() {
        Step bad = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                throw new RuntimeException("enter-boom");
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.stay();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", bad).build();
        worker.submit(flow);

        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.failureCause()).hasMessage("enter-boom");
    }

    @Test
    void fail_result_preserves_primary_cause_when_onExit_throws() {
        Step bad = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.fail(new RuntimeException("primary"));
            }

            @Override
            protected void onExit(StepContext ctx) {
                throw new RuntimeException("exit");
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", bad).build();
        worker.submit(flow);

        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.failureCause()).hasMessage("primary");
    }

    @Test
    void terminal_snapshot_has_no_current_step() {
        Step done = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.done();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", done).build();
        worker.submit(flow);

        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(flow.snapshot().currentStepId()).isNull();
    }
}
