package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.SystemClock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventWorkerBackgroundTest {

    static final class Wake {
    }

    @Test
    void backgroundWorkerWakesWhenEventArrives() throws Exception {
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = new EventWorker("bg-event", SystemClock.INSTANCE, bus);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch awaiting = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        EventFlow flow = EventFlow.builder("bg", "event")
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        entered.countDown();
                        return EventStepResult.await(AwaitCondition.event(Wake.class))
                                .thenRun(c -> awaiting.countDown());
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        finished.countDown();
                        return EventStepResult.finish();
                    }
                })
                .build();

        try {
            worker.start();
            worker.submit(flow);

            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(awaiting.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(worker.stateOf(flow.flowId())).isEqualTo(FlowState.RUNNING);

            bus.publish(new Wake());

            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
            awaitInactive(worker);
            assertThat(worker.stateOf(flow.flowId())).isNull();
            assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        } finally {
            worker.stop();
        }
    }

    @Test
    void backgroundWorkerWakesForDeadline() throws Exception {
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = new EventWorker("bg-deadline", SystemClock.INSTANCE, bus);
        CountDownLatch timedOut = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        EventFlow flow = EventFlow.builder("bg", "deadline")
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.deadlineIn(25));
                    }

                    @Override
                    protected EventStepResult onTimeout(EventStepContext ctx) {
                        timedOut.countDown();
                        return EventStepResult.finish();
                    }
                })
                .build();

        try {
            worker.start();
            worker.submit(flow);

            assertThat(timedOut.await(1, TimeUnit.SECONDS)).isTrue();
            awaitInactive(worker);
            assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        } catch (Throwable t) {
            failure.set(t);
            throw t;
        } finally {
            worker.stop();
        }

        assertThat(failure.get()).isNull();
    }

    @Test
    void drainIsRejectedWhileBackgroundWorkerIsRunning() {
        EventWorker worker = new EventWorker("bg-drain", SystemClock.INSTANCE, InMemoryEventBus.create());
        try {
            worker.start();

            assertThatThrownBy(worker::drain)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("background mode");
        } finally {
            worker.stop();
        }
    }

    private static void awaitInactive(EventWorker worker) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 1_000L;
        while (worker.activeCount() != 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(worker.activeCount()).isZero();
    }
}
