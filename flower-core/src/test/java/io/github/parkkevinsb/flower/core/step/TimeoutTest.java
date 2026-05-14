package io.github.parkkevinsb.flower.core.step;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeoutTest {

    private ManualClock clock;
    private Worker worker;
    private Engine engine;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000);
        worker = Worker.builder("test").build();
        engine = Engine.builder().clock(clock).eventBus(InMemoryEventBus.create()).worker(worker).build();
        engine.attach();
    }

    @Test
    void timedOut_is_false_until_clock_advances_past_deadline() {
        Step step = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                ctx.startTimeout(5_000);
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                return ctx.timedOut() ? StepResult.done() : StepResult.stay();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", step).build();
        worker.submit(flow);

        worker.tickOnce(); // not timed out yet
        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);

        clock.advance(4_999);
        worker.tickOnce();
        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);

        clock.advance(1); // now elapsed >= 5000
        worker.tickOnce();
        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
    }

    @Test
    void elapsedMillis_grows_with_clock() {
        long[] elapsedSeen = {-1L};
        Step step = new Step() {
            boolean started;

            @Override
            protected void onEnter(StepContext ctx) {
                ctx.startTimeout(10_000); // recorded at clock=1000
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                if (!started) {
                    started = true;
                    return StepResult.stay(); // first tick: stay so we can advance the clock
                }
                elapsedSeen[0] = ctx.elapsedMillis();
                return StepResult.done();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", step).build();
        worker.submit(flow);

        worker.tickOnce(); // enter (startTimeout @1000), tick #1 (started=true, stay)
        clock.advance(2_500);
        worker.tickOnce(); // tick #2: elapsed = 2500, done

        assertThat(elapsedSeen[0]).isEqualTo(2_500L);
    }
}
