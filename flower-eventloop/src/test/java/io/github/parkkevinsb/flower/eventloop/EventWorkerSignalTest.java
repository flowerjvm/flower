package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EventWorkerSignalTest {

    @Test
    void signalAwaitWakesMatchingFlow() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = new EventWorker("signal", clock, bus);
        AtomicReference<EventSignal> received = new AtomicReference<>();

        EventFlow flow = EventFlow.builder("callback", "tool-1")
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.signal("tool-call", "call-1"));
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        received.set((EventSignal) event);
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();
        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);

        worker.signal("tool-call", "call-1", "ok");
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(received.get().name()).isEqualTo("tool-call");
        assertThat(received.get().key()).isEqualTo("call-1");
        assertThat(received.get().payload(String.class)).isEqualTo("ok");
    }

    @Test
    void nonMatchingSignalIsIgnored() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = new EventWorker("signal", clock, bus);

        EventFlow flow = EventFlow.builder("callback", "tool-2")
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.signal("tool-call", "call-2"));
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        worker.signal("tool-call", "other-call");
        worker.signal("other-signal", "call-2");
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);
        assertThat(worker.activeCount()).isEqualTo(1);
    }

    @Test
    void contextCanPublishSignal() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = new EventWorker("signal", clock, bus);

        EventFlow waiter = EventFlow.builder("callback", "waiter")
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.signal("approval", "A-1"));
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        EventFlow publisher = EventFlow.builder("callback", "publisher")
                .step("publish", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        ctx.signal("approval", "A-1");
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(waiter);
        worker.drain();
        worker.submit(publisher);
        worker.drain();

        assertThat(waiter.state()).isEqualTo(FlowState.FINISHED);
        assertThat(publisher.state()).isEqualTo(FlowState.FINISHED);
    }
}
