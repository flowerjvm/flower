package io.github.flowerjvm.flower.eventloop;

import io.github.flowerjvm.flower.eventloop.flow.EventFlow;
import io.github.flowerjvm.flower.eventloop.step.AwaitCondition;
import io.github.flowerjvm.flower.eventloop.step.EventStep;
import io.github.flowerjvm.flower.eventloop.step.EventStepContext;
import io.github.flowerjvm.flower.eventloop.step.EventStepResult;
import io.github.flowerjvm.flower.eventloop.worker.EventWorker;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.eventloop.persistence.EventFlowCheckpointStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class EventWorkerAsyncTest {

    static final class Response {
    }

    @Test
    void asyncEffectPublishesResponseAfterAwaitIsRegistered() {
        QueuedExecutor executor = new QueuedExecutor();
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = EventWorker.builder("async")
                .clock(new ManualClock())
                .eventBus(bus)
                .asyncExecutor(executor)
                .build();

        EventFlow flow = EventFlow.builder("async", "ok")
                .step("call", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.event(Response.class))
                                .thenRunAsync(c -> c.eventBus().publish(new Response()));
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
    void asyncWithoutExecutorFailsFlowFast() {
        EventWorker worker = EventWorker.builder("async-missing")
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .build();

        EventFlow flow = EventFlow.builder("async", "missing")
                .step("call", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.event(Response.class))
                                .thenRunAsync(c -> c.eventBus().publish(new Response()));
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.failureCause()).isInstanceOf(IllegalStateException.class);
        assertThat(flow.failureCause()).hasMessageContaining("no async executor");
    }

    @Test
    void asyncTaskFailureIsReportedAsWorkerError() {
        QueuedExecutor executor = new QueuedExecutor();
        RecordingListener listener = new RecordingListener();
        EventWorker worker = EventWorker.builder("async-error")
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .checkpointStore(EventFlowCheckpointStore.NOOP)
                .listeners(Collections.singletonList(listener))
                .asyncExecutor(executor)
                .build();

        EventFlow flow = EventFlow.builder("async", "task-error")
                .step("call", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.event(Response.class))
                                .thenRunAsync(c -> {
                                    throw new IllegalStateException("async boom");
                                });
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();
        executor.runNext();

        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);
        assertThat(listener.workerError).isEqualTo("async-error:async boom");
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
            throw new AssertionError("async task failure should not synchronously fail flow");
        }
    }
}
