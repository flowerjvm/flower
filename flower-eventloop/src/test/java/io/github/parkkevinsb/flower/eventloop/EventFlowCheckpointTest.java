package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.eventloop.flow.EventFlow;
import io.github.parkkevinsb.flower.eventloop.step.AwaitCondition;
import io.github.parkkevinsb.flower.eventloop.step.EventStep;
import io.github.parkkevinsb.flower.eventloop.step.EventStepContext;
import io.github.parkkevinsb.flower.eventloop.step.EventStepResult;
import io.github.parkkevinsb.flower.eventloop.worker.EventWorker;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.eventloop.persistence.EventAwaitCheckpoint;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventFlowCheckpointTest {

    static final class Response {
    }

    @Test
    void durableFlowSavesAwaitCheckpointBeforeEffectsRun() {
        ManualClock clock = new ManualClock(1_000L);
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        EventWorker worker = EventWorker.builder("durable")
                .clock(clock)
                .eventBus(bus)
                .checkpointStore(store)
                .build();
        FlowId flowId = FlowId.of("durable", "await");

        EventFlow flow = EventFlow.builder("durable", "await")
                .durable()
                .definitionVersion("v1")
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(
                                AwaitCondition.event(Response.class),
                                AwaitCondition.deadlineIn(500));
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        EventFlowCheckpoint checkpoint = store.find(flowId).orElseThrow(AssertionError::new);
        assertThat(checkpoint.flowId()).isEqualTo(flowId);
        assertThat(checkpoint.state()).isEqualTo(FlowState.RUNNING);
        assertThat(checkpoint.currentStepId()).isEqualTo("wait");
        assertThat(checkpoint.currentStepEntered()).isTrue();
        assertThat(checkpoint.persistence()).isEqualTo(FlowPersistence.DURABLE);
        assertThat(checkpoint.workerName()).isEqualTo("durable");
        assertThat(checkpoint.updatedAtMillis()).isEqualTo(1_000L);
        assertThat(checkpoint.definitionVersion()).isEqualTo("v1");
        assertThat(checkpoint.awaitGeneration()).isEqualTo(1L);

        List<EventAwaitCheckpoint> awaits = checkpoint.awaits();
        assertThat(awaits).hasSize(2);
        assertThat(awaits.get(0).type()).isEqualTo(EventAwaitCheckpoint.Type.EVENT);
        assertThat(awaits.get(0).eventTypeName()).isEqualTo(Response.class.getName());
        assertThat(awaits.get(1).type()).isEqualTo(EventAwaitCheckpoint.Type.DEADLINE);
        assertThat(awaits.get(1).deadlineAtMillis()).isEqualTo(1_500L);
    }

    @Test
    void durableSignalAwaitIsCheckpointedAsData() {
        ManualClock clock = new ManualClock(1_000L);
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        EventWorker worker = EventWorker.builder("durable")
                .clock(clock)
                .eventBus(bus)
                .checkpointStore(store)
                .build();

        EventFlow flow = EventFlow.builder("durable", "signal")
                .durable()
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(
                                AwaitCondition.signal("tool-call", "call-1"),
                                AwaitCondition.deadlineIn(500));
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        EventFlowCheckpoint checkpoint = store.find(flow.flowId()).orElseThrow(AssertionError::new);
        assertThat(checkpoint.awaits()).hasSize(2);
        assertThat(checkpoint.awaits().get(0).type()).isEqualTo(EventAwaitCheckpoint.Type.SIGNAL);
        assertThat(checkpoint.awaits().get(0).signalName()).isEqualTo("tool-call");
        assertThat(checkpoint.awaits().get(0).signalKey()).isEqualTo("call-1");
        assertThat(checkpoint.awaits().get(1).type()).isEqualTo(EventAwaitCheckpoint.Type.DEADLINE);
    }

    @Test
    void finishingDurableFlowDeletesCheckpoint() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        EventWorker worker = EventWorker.builder("durable")
                .clock(clock)
                .eventBus(bus)
                .checkpointStore(store)
                .build();
        FlowId flowId = FlowId.of("durable", "finish");

        EventFlow flow = EventFlow.builder("durable", "finish")
                .durable()
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.event(Response.class));
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();
        assertThat(store.find(flowId)).isPresent();

        bus.publish(new Response());
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(store.find(flowId)).isEmpty();
        assertThat(store.deleteCount()).isEqualTo(1);
    }

    @Test
    void cancellingDurableFlowDeletesCheckpoint() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        EventWorker worker = EventWorker.builder("durable")
                .clock(clock)
                .eventBus(bus)
                .checkpointStore(store)
                .build();
        FlowId flowId = FlowId.of("durable", "cancel");

        EventFlow flow = EventFlow.builder("durable", "cancel")
                .durable()
                .step("wait", new AwaitForeverStep())
                .build();

        worker.submit(flow);
        worker.drain();
        assertThat(store.find(flowId)).isPresent();

        assertThat(worker.cancel(flowId)).isTrue();
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(store.find(flowId)).isEmpty();
        assertThat(store.deleteCount()).isEqualTo(1);
    }

    @Test
    void durablePredicateAwaitFailsBecauseItCannotBeCheckpointedYet() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        EventWorker worker = EventWorker.builder("durable")
                .clock(clock)
                .eventBus(bus)
                .checkpointStore(store)
                .build();
        FlowId flowId = FlowId.of("durable", "predicate");

        EventFlow flow = EventFlow.builder("durable", "predicate")
                .durable()
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(
                                AwaitCondition.event(Response.class, response -> true));
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.failureCause()).isInstanceOf(IllegalStateException.class);
        assertThat(flow.failureCause()).hasMessageContaining("predicate-based event await");
        assertThat(worker.stateOf(flowId)).isNull();
        assertThat(store.find(flowId)).isEmpty();
    }

    @Test
    void stoppingWorkerKeepsDurableCheckpointAndDoesNotCancelFlow() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        EventWorker worker = EventWorker.builder("durable")
                .clock(clock)
                .eventBus(bus)
                .checkpointStore(store)
                .build();

        EventFlow flow = EventFlow.builder("durable", "stop")
                .durable()
                .step("wait", new AwaitForeverStep())
                .build();

        worker.submit(flow);
        worker.drain();

        worker.stop();

        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);
        assertThat(worker.activeCount()).isZero();
        assertThat(store.find(flow.flowId())).isPresent();
    }

    static final class AwaitForeverStep extends EventStep {
        @Override
        protected EventStepResult onEnter(EventStepContext ctx) {
            return EventStepResult.await(AwaitCondition.event(Response.class));
        }
    }
}
