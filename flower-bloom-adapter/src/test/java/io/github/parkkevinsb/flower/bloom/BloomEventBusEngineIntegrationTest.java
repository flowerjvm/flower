package io.github.parkkevinsb.flower.bloom;

import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check that a Flower {@link Engine} backed by a Bloom-bridged
 * {@link io.github.parkkevinsb.flower.core.event.EventBus} behaves the same
 * as one backed by the in-memory bus from core.
 *
 * <p>The point of this test is to exercise the boundary between flower-core
 * and the Bloom adapter under the actual Step subscription lifecycle, not to
 * re-test core mechanics.
 */
class BloomEventBusEngineIntegrationTest {

    static final class Ping {
        final String tag;
        Ping(String tag) { this.tag = tag; }
    }

    @Test
    void external_publish_through_bloom_signals_step_and_completes_on_following_tick() {
        ManualClock clock = new ManualClock();
        LocalEventBus bloom = LocalEventBus.create();

        Worker worker = Worker.builder("test").build();
        Engine engine = Engine.builder()
                .clock(clock)
                .eventBus(BloomEventBus.wrap(bloom))
                .worker(worker)
                .build();
        engine.attach();

        AtomicInteger ticks = new AtomicInteger();
        Step waiter = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                ctx.subscribe(Ping.class, e -> ctx.signal("ping"));
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                ticks.incrementAndGet();
                return ctx.hasSignal("ping") ? StepResult.done() : StepResult.stay();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", waiter).build();
        worker.submit(flow);

        worker.tickOnce(); // enter + tick #1: no signal -> stay
        bloom.publish(new Ping("a")); // raw bloom publish
        worker.tickOnce(); // tick #2: signal seen -> done -> FINISHED

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(ticks.get()).isEqualTo(2);
    }

    @Test
    void step_exit_disposes_subscription_through_bloom() {
        ManualClock clock = new ManualClock();
        LocalEventBus bloom = LocalEventBus.create();

        Worker worker = Worker.builder("test").build();
        Engine engine = Engine.builder()
                .clock(clock)
                .eventBus(BloomEventBus.wrap(bloom))
                .worker(worker)
                .build();
        engine.attach();

        AtomicInteger received = new AtomicInteger();
        Step quickDone = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                ctx.subscribe(Ping.class, e -> received.incrementAndGet());
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.done();
            }
        };
        Step terminal = new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.done();
            }
        };
        Flow flow = Flow.builder("test", "1")
                .step("waiter", quickDone)
                .step("end", terminal)
                .build();
        worker.submit(flow);

        worker.tickOnce(); // waiter enters (subscribe), done, exits (unsubscribe)
        bloom.publish(new Ping("after-exit"));
        worker.tickOnce(); // end enters, ticks, done

        assertThat(received.get()).isZero();
        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
    }
}
