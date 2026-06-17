package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class EventWorkerOffloadTest {

    static final class Response {
    }

    @Test
    void offloadedEffectPublishesResponseAfterAwaitIsRegistered() {
        QueuedExecutor executor = new QueuedExecutor();
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = new EventWorker("offload", new ManualClock(), bus, executor);

        EventFlow flow = EventFlow.builder("offload", "ok")
                .step("call", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.event(Response.class))
                                .thenRunOffloaded(c -> c.eventBus().publish(new Response()));
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);
        assertThat(executor.size()).isEqualTo(1);

        executor.runNext();
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
    }

    @Test
    void offloadWithoutExecutorFailsFlowFast() {
        EventWorker worker = new EventWorker("offload-missing", new ManualClock(), InMemoryEventBus.create());

        EventFlow flow = EventFlow.builder("offload", "missing")
                .step("call", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.event(Response.class))
                                .thenRunOffloaded(c -> c.eventBus().publish(new Response()));
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.failureCause()).isInstanceOf(IllegalStateException.class);
        assertThat(flow.failureCause()).hasMessageContaining("no offload executor");
    }

    @Test
    void offloadedTaskFailureIsReportedAsWorkerError() {
        QueuedExecutor executor = new QueuedExecutor();
        RecordingListener listener = new RecordingListener();
        EventWorker worker = new EventWorker(
                "offload-error",
                new ManualClock(),
                InMemoryEventBus.create(),
                EventFlowCheckpointStore.NOOP,
                Collections.singletonList(listener),
                executor);

        EventFlow flow = EventFlow.builder("offload", "task-error")
                .step("call", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.event(Response.class))
                                .thenRunOffloaded(c -> {
                                    throw new IllegalStateException("offload boom");
                                });
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();
        executor.runNext();

        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);
        assertThat(listener.workerError).isEqualTo("offload-error:offload boom");
    }

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int size() {
            return tasks.size();
        }

        void runNext() {
            tasks.remove().run();
        }
    }

    private static final class RecordingListener implements FlowerListener {
        private String workerError;

        @Override
        public void onWorkerError(String workerName, Throwable cause) {
            workerError = workerName + ":" + cause.getMessage();
        }

        @Override
        public void onFlowFailed(FlowSnapshot flow, Throwable cause) {
            throw new AssertionError("offloaded task failure should not synchronously fail flow");
        }
    }
}
