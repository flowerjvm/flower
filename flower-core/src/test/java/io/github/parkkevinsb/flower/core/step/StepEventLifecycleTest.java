package io.github.parkkevinsb.flower.core.step;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.event.Subscription;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StepEventLifecycleTest {

    static final class Ping {
        final String tag;
        Ping(String tag) { this.tag = tag; }
    }

    static final class Ack {
    }

    static final class Arrival {
    }

    private ManualClock clock;
    private InMemoryEventBus bus;
    private Worker worker;
    private Engine engine;

    @BeforeEach
    void setUp() {
        clock = new ManualClock();
        bus = InMemoryEventBus.create();
        worker = Worker.builder("test").build();
        engine = Engine.builder().clock(clock).eventBus(bus).worker(worker).build();
        engine.attach();
    }

    @Test
    void event_arrives_as_signal_then_completes_on_following_tick() {
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
        bus.publish(new Ping("a")); // sets signal on event-bus thread
        worker.tickOnce(); // tick #2: signal seen -> done -> FINISHED

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(ticks.get()).isEqualTo(2);
    }

    @Test
    void subscription_disposed_on_step_exit() {
        AtomicInteger received = new AtomicInteger();
        StepRuntime[] capturedRuntime = new StepRuntime[1];
        Step quickDone = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                capturedRuntime[0] = (StepRuntime) ctx;
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

        worker.tickOnce(); // enter waiter (subscribe), tick -> done -> exit (unsubscribe)

        // After exit, the runtime should have no surviving subscriptions.
        assertThat(capturedRuntime[0].subscriptionCount()).isZero();

        bus.publish(new Ping("a")); // must not reach the disposed handler
        assertThat(received.get()).isZero();
    }

    @Test
    void manual_unsubscribe_removes_only_that_subscription_during_step_execution() {
        AtomicInteger ackReceived = new AtomicInteger();
        AtomicInteger arrivalReceived = new AtomicInteger();
        StepRuntime[] capturedRuntime = new StepRuntime[1];

        Step step = new Step() {
            private Subscription arrivalSub;

            @Override
            protected void onEnter(StepContext ctx) {
                capturedRuntime[0] = (StepRuntime) ctx;
                ctx.subscribe(Ack.class, this::onAck);
                subscribeArrival(ctx);
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                if (ctx.stepNo() == 0) {
                    unsubscribeArrival();
                    ctx.setStepNo(10);
                    return StepResult.stay();
                }
                if (ctx.stepNo() == 10) {
                    subscribeArrival(ctx);
                    ctx.setStepNo(20);
                    return StepResult.stay();
                }
                return StepResult.done();
            }

            private void onAck(Ack event) {
                ackReceived.incrementAndGet();
            }

            private void onArrival(Arrival event) {
                arrivalReceived.incrementAndGet();
            }

            private void subscribeArrival(StepContext ctx) {
                if (arrivalSub == null) {
                    arrivalSub = ctx.subscribe(Arrival.class, this::onArrival);
                }
            }

            private void unsubscribeArrival() {
                if (arrivalSub != null) {
                    arrivalSub.unsubscribe();
                    arrivalSub = null;
                }
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", step).build();
        worker.submit(flow);

        worker.tickOnce(); // enter: ack+arrival, tick: unsubscribe arrival
        assertThat(capturedRuntime[0].subscriptionCount()).isEqualTo(1);

        bus.publish(new Arrival());
        bus.publish(new Ack());
        assertThat(arrivalReceived.get()).isZero();
        assertThat(ackReceived.get()).isEqualTo(1);

        worker.tickOnce(); // resubscribe arrival
        assertThat(capturedRuntime[0].subscriptionCount()).isEqualTo(2);

        bus.publish(new Arrival());
        assertThat(arrivalReceived.get()).isEqualTo(1);

        worker.tickOnce(); // done -> exit -> dispose remaining subscriptions
        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(capturedRuntime[0].subscriptionCount()).isZero();
    }

    @Test
    void subscription_disposed_on_repeat_reset() {
        AtomicInteger received = new AtomicInteger();
        int[] entryCount = {0};
        Step waiter = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                entryCount[0]++;
                ctx.subscribe(Ping.class, e -> received.incrementAndGet());
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                if (entryCount[0] == 1) return StepResult.repeat();
                return StepResult.done();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", waiter).build();
        worker.submit(flow);

        worker.tickOnce(); // enter#1 (subscribe), tick -> repeat -> reset (unsubscribe)
        bus.publish(new Ping("a")); // subscription from entry#1 should be gone

        worker.tickOnce(); // enter#2 (new subscribe), tick -> done

        assertThat(received.get()).isZero();
        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
    }

    @Test
    void clearSignal_works() {
        Step step = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                ctx.signal("a");
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                if (ctx.hasSignal("a")) {
                    ctx.clearSignal("a");
                    return StepResult.stay();
                }
                return StepResult.done();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", step).build();
        worker.submit(flow);

        worker.tickOnce(); // sees signal, clears, stays
        worker.tickOnce(); // no signal, done -> finished

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
    }

    @Test
    void payload_signal_keeps_latest_value_and_can_be_consumed() {
        List<String> seen = new ArrayList<>();
        List<Boolean> presentAfterConsume = new ArrayList<>();
        Step step = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                ctx.subscribe(Ping.class, event -> ctx.signal("ping", event));
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                Ping peek = ctx.signalPayload("ping", Ping.class);
                if (peek == null) {
                    return StepResult.stay();
                }

                seen.add("peek:" + peek.tag);
                seen.add("present-before-consume:" + ctx.hasSignal("ping"));
                Ping consumed = ctx.consumeSignal("ping", Ping.class);
                seen.add("consume:" + consumed.tag);
                presentAfterConsume.add(ctx.hasSignal("ping"));
                return StepResult.done();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", step).build();
        worker.submit(flow);

        worker.tickOnce(); // enter + tick #1: no payload yet -> stay
        bus.publish(new Ping("old"));
        bus.publish(new Ping("latest"));
        worker.tickOnce(); // tick #2: latest payload is consumed -> finished

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(seen).containsExactly(
                "peek:latest",
                "present-before-consume:true",
                "consume:latest");
        assertThat(presentAfterConsume).containsExactly(false);
    }

    @Test
    void consumeSignal_clears_payloadless_signal() {
        List<String> seen = new ArrayList<>();
        Step step = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                ctx.signal("ready");
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                if (!ctx.hasSignal("ready")) {
                    return StepResult.done();
                }
                Ping payload = ctx.consumeSignal("ready", Ping.class);
                seen.add(String.valueOf(payload));
                seen.add("present:" + ctx.hasSignal("ready"));
                return StepResult.stay();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", step).build();
        worker.submit(flow);

        worker.tickOnce(); // consumes payloadless signal and stays
        worker.tickOnce(); // no signal remains, done -> finished

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(seen).containsExactly("null", "present:false");
    }

    @Test
    void engine_stop_cancels_flow_and_disposes_subscription() {
        AtomicInteger received = new AtomicInteger();
        Step waiter = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                ctx.subscribe(Ping.class, e -> received.incrementAndGet());
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.stay();
            }
        };
        Flow flow = Flow.builder("test", "1").step("only", waiter).build();
        worker.submit(flow);

        worker.tickOnce();
        engine.stop();
        bus.publish(new Ping("after-stop"));

        assertThat(flow.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(received.get()).isZero();
    }
}
