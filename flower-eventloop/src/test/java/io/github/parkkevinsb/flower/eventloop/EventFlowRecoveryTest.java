package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.eventloop.checkpoint.EventAwaitCheckpoint;
import io.github.parkkevinsb.flower.eventloop.checkpoint.EventFlowCheckpoint;
import io.github.parkkevinsb.flower.eventloop.recovery.EventFlowFactoryRegistry;
import io.github.parkkevinsb.flower.eventloop.recovery.EventFlowRecoveryService;
import io.github.parkkevinsb.flower.eventloop.recovery.EventRecoveryContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EventFlowRecoveryTest {

    static final class Response {
    }

    @Test
    void recoveryReRegistersEventAwaitWithoutRepeatingEnterEffect() {
        ManualClock clock = new ManualClock(2_000L);
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        EventWorker worker = new EventWorker("worker-a", clock, bus, store);
        FlowId flowId = FlowId.of("recover", "event");
        AtomicInteger enterEffects = new AtomicInteger();
        AtomicInteger recoveries = new AtomicInteger();

        store.save(checkpoint(flowId, "worker-a",
                Collections.singletonList(EventAwaitCheckpoint.event(Response.class.getName()))));

        EventFlowRecoveryService recovery = EventFlowRecoveryService.create(
                store,
                EventFlowFactoryRegistry.builder()
                        .register("recover", id -> EventFlow.builder(id.flowType(), id.flowKey())
                                .durable()
                                .definitionVersion("v1")
                                .step("wait", new EventStep() {
                                    @Override
                                    protected EventStepResult onEnter(EventStepContext ctx) {
                                        return EventStepResult.await(AwaitCondition.event(Response.class))
                                                .thenRun(c -> enterEffects.incrementAndGet());
                                    }

                                    @Override
                                    protected EventStepResult onRecover(
                                            EventStepContext ctx,
                                            EventRecoveryContext recovery) {
                                        recoveries.incrementAndGet();
                                        assertThat(recovery.awaits()).hasSize(1);
                                        assertThat(recovery.awaits().get(0).eventTypeName())
                                                .isEqualTo(Response.class.getName());
                                        return EventStepResult.await(AwaitCondition.event(Response.class));
                                    }

                                    @Override
                                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                                        return EventStepResult.finish();
                                    }
                                })
                                .build())
                        .build());

        EventFlow flow = recovery.recover(store.find(flowId).orElseThrow(AssertionError::new), worker);
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);
        assertThat(enterEffects.get()).isZero();
        assertThat(recoveries.get()).isEqualTo(1);
        assertThat(store.find(flowId)).isPresent();

        bus.publish(new Response());
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(store.find(flowId)).isEmpty();
        assertThat(store.deleteCount()).isEqualTo(1);
    }

    @Test
    void recoveryFiresElapsedDeadlineImmediately() {
        ManualClock clock = new ManualClock(2_000L);
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        EventWorker worker = new EventWorker("worker-a", clock, bus, store);
        FlowId flowId = FlowId.of("recover", "deadline");
        AtomicInteger timeouts = new AtomicInteger();

        store.save(checkpoint(flowId, "worker-a",
                Collections.singletonList(EventAwaitCheckpoint.deadline(1_000L))));

        EventFlowRecoveryService recovery = EventFlowRecoveryService.create(
                store,
                EventFlowFactoryRegistry.builder()
                        .register("recover", id -> EventFlow.builder(id.flowType(), id.flowKey())
                                .durable()
                                .definitionVersion("v1")
                                .step("wait", new EventStep() {
                                    @Override
                                    protected EventStepResult onEnter(EventStepContext ctx) {
                                        return EventStepResult.await(AwaitCondition.deadlineIn(10_000L));
                                    }

                                    @Override
                                    protected EventStepResult onRecover(
                                            EventStepContext ctx,
                                            EventRecoveryContext recovery) {
                                        EventAwaitCheckpoint deadline = recovery.awaits().get(0);
                                        return EventStepResult.await(
                                                AwaitCondition.deadlineIn(recovery.millisUntil(deadline, ctx.now())));
                                    }

                                    @Override
                                    protected EventStepResult onTimeout(EventStepContext ctx) {
                                        timeouts.incrementAndGet();
                                        return EventStepResult.finish();
                                    }
                                })
                                .build())
                        .build());

        EventFlow flow = recovery.recover(store.find(flowId).orElseThrow(AssertionError::new), worker);
        worker.drain();

        assertThat(timeouts.get()).isEqualTo(1);
        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(store.find(flowId)).isEmpty();
    }

    @Test
    void recoveryReRegistersSignalAwait() {
        ManualClock clock = new ManualClock(2_000L);
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        EventWorker worker = new EventWorker("worker-a", clock, bus, store);
        FlowId flowId = FlowId.of("recover", "signal");

        store.save(checkpoint(flowId, "worker-a",
                Collections.singletonList(EventAwaitCheckpoint.signal("tool-call", "call-1"))));

        EventFlowRecoveryService recovery = EventFlowRecoveryService.create(
                store,
                EventFlowFactoryRegistry.builder()
                        .register("recover", id -> EventFlow.builder(id.flowType(), id.flowKey())
                                .durable()
                                .definitionVersion("v1")
                                .step("wait", new EventStep() {
                                    @Override
                                    protected EventStepResult onEnter(EventStepContext ctx) {
                                        return EventStepResult.await(AwaitCondition.signal("tool-call", "call-1"));
                                    }

                                    @Override
                                    protected EventStepResult onRecover(
                                            EventStepContext ctx,
                                            EventRecoveryContext recovery) {
                                        EventAwaitCheckpoint await = recovery.awaits().get(0);
                                        assertThat(await.signalName()).isEqualTo("tool-call");
                                        assertThat(await.signalKey()).isEqualTo("call-1");
                                        return EventStepResult.await(
                                                AwaitCondition.signal(await.signalName(), await.signalKey()));
                                    }

                                    @Override
                                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                                        EventSignal signal = (EventSignal) event;
                                        assertThat(signal.payload(String.class)).isEqualTo("ok");
                                        return EventStepResult.finish();
                                    }
                                })
                                .build())
                        .build());

        EventFlow flow = recovery.recover(store.find(flowId).orElseThrow(AssertionError::new), worker);
        worker.drain();
        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);

        worker.signal("tool-call", "call-1", "ok");
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(store.find(flowId)).isEmpty();
    }

    @Test
    void defaultOnRecoverFailsFast() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        EventWorker worker = new EventWorker("worker-a", clock, bus, store);
        FlowId flowId = FlowId.of("recover", "missing-on-recover");

        store.save(checkpoint(flowId, "worker-a",
                Collections.singletonList(EventAwaitCheckpoint.event(Response.class.getName()))));

        EventFlowRecoveryService recovery = EventFlowRecoveryService.create(
                store,
                EventFlowFactoryRegistry.builder()
                        .register("recover", id -> EventFlow.builder(id.flowType(), id.flowKey())
                                .durable()
                                .definitionVersion("v1")
                                .step("wait", new EventStep() {
                                    @Override
                                    protected EventStepResult onEnter(EventStepContext ctx) {
                                        return EventStepResult.await(AwaitCondition.event(Response.class));
                                    }
                                })
                                .build())
                        .build());

        EventFlow flow = recovery.recover(store.find(flowId).orElseThrow(AssertionError::new), worker);
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.failureCause()).isInstanceOf(IllegalStateException.class);
        assertThat(flow.failureCause()).hasMessageContaining("does not implement onRecover");
        assertThat(store.find(flowId)).isEmpty();
    }

    @Test
    void recoverActiveForWorkerSubmitsOnlyMatchingCheckpoints() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        EventWorker worker = new EventWorker("worker-a", clock, bus, store);

        store.save(checkpoint(FlowId.of("recover", "a"), "worker-a",
                Collections.singletonList(EventAwaitCheckpoint.event(Response.class.getName()))));
        store.save(checkpoint(FlowId.of("recover", "b"), "worker-b",
                Collections.singletonList(EventAwaitCheckpoint.event(Response.class.getName()))));

        EventFlowRecoveryService recovery = EventFlowRecoveryService.create(
                store,
                EventFlowFactoryRegistry.builder()
                        .register("recover", id -> EventFlow.builder(id.flowType(), id.flowKey())
                                .durable()
                                .definitionVersion("v1")
                                .step("wait", new RecoveringAwaitStep())
                                .build())
                        .build());

        int recovered = recovery.recoverActiveForWorker(worker);
        worker.drain();

        assertThat(recovered).isEqualTo(1);
        assertThat(worker.activeCount()).isEqualTo(1);
        assertThat(store.find(FlowId.of("recover", "a"))).isPresent();
        assertThat(store.find(FlowId.of("recover", "b"))).isPresent();
    }

    private static EventFlowCheckpoint checkpoint(
            FlowId flowId,
            String workerName,
            java.util.List<EventAwaitCheckpoint> awaits) {
        return new EventFlowCheckpoint(
                flowId,
                FlowState.RUNNING,
                "wait",
                true,
                FlowPersistence.DURABLE,
                workerName,
                1_000L,
                "v1",
                ExecutionContext.empty(),
                7L,
                awaits);
    }

    static final class RecoveringAwaitStep extends EventStep {
        @Override
        protected EventStepResult onEnter(EventStepContext ctx) {
            return EventStepResult.await(AwaitCondition.event(Response.class));
        }

        @Override
        protected EventStepResult onRecover(EventStepContext ctx, EventRecoveryContext recovery) {
            return EventStepResult.await(AwaitCondition.event(Response.class));
        }
    }
}
