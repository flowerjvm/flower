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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void stop_waits_for_active_tick_before_canceling_flow() throws Exception {
        CountDownLatch enteredTick = new CountDownLatch(1);
        CountDownLatch releaseTick = new CountDownLatch(1);
        CountDownLatch stopReturned = new CountDownLatch(1);
        AtomicReference<Throwable> tickFailure = new AtomicReference<>();
        AtomicReference<Throwable> stopFailure = new AtomicReference<>();

        Flow flow = Flow.builder("t", "blocking")
                .step("only", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        enteredTick.countDown();
                        try {
                            if (!releaseTick.await(2, TimeUnit.SECONDS)) {
                                return StepResult.fail(new AssertionError("tick was not released"));
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return StepResult.fail(e);
                        }
                        return StepResult.stay();
                    }
                })
                .build();
        worker.submit(flow);

        Thread tickThread = new Thread(() -> {
            try {
                worker.tickOnce();
            } catch (Throwable t) {
                tickFailure.set(t);
            }
        });
        tickThread.start();
        assertThat(enteredTick.await(1, TimeUnit.SECONDS)).isTrue();

        Thread stopThread = new Thread(() -> {
            try {
                worker.stop();
            } catch (Throwable t) {
                stopFailure.set(t);
            } finally {
                stopReturned.countDown();
            }
        });
        stopThread.start();

        assertThat(stopReturned.await(100, TimeUnit.MILLISECONDS)).isFalse();

        releaseTick.countDown();
        assertThat(stopReturned.await(1, TimeUnit.SECONDS)).isTrue();
        tickThread.join(1_000);
        stopThread.join(1_000);

        assertThat(tickThread.isAlive()).isFalse();
        assertThat(stopThread.isAlive()).isFalse();
        assertThat(tickFailure.get()).isNull();
        assertThat(stopFailure.get()).isNull();
        assertThat(flow.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(worker.state()).isEqualTo(WorkerState.STOPPED);
    }
}
