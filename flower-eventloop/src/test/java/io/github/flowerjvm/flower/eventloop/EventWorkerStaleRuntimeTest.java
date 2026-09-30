package io.github.flowerjvm.flower.eventloop;

import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.worker.DuplicatePolicy;
import io.github.flowerjvm.flower.eventloop.flow.EventFlow;
import io.github.flowerjvm.flower.eventloop.step.AwaitCondition;
import io.github.flowerjvm.flower.eventloop.step.EventStep;
import io.github.flowerjvm.flower.eventloop.step.EventStepContext;
import io.github.flowerjvm.flower.eventloop.step.EventStepResult;
import io.github.flowerjvm.flower.eventloop.worker.EventWorker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EventWorkerStaleRuntimeTest {

    @Test
    void terminalListenerReplacementIsNotRemovedWithOldRuntime() {
        InMemoryEventBus bus = InMemoryEventBus.create();
        AtomicReference<EventWorker> workerRef = new AtomicReference<>();
        AtomicInteger replacementEntries = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        EventFlow replacement = EventFlow.builder("job", "same")
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        replacementEntries.incrementAndGet();
                        return EventStepResult.await(AwaitCondition.signal("done", "same"));
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        return EventStepResult.finish();
                    }
                })
                .build();
        EventWorker worker = EventWorker.builder("replacement")
                .clock(new ManualClock())
                .eventBus(bus)
                .listener(new FlowerListener() {
                    @Override
                    public void onFlowFinished(FlowSnapshot flow) {
                        if (completions.getAndIncrement() == 0) {
                            workerRef.get().submit(replacement, DuplicatePolicy.REPLACE);
                        }
                    }
                })
                .build();
        workerRef.set(worker);
        EventFlow original = EventFlow.builder("job", "same")
                .step("finish", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(original);
        worker.drain();

        assertThat(original.state()).isEqualTo(FlowState.FINISHED);
        assertThat(replacement.state()).isEqualTo(FlowState.RUNNING);
        assertThat(replacementEntries).hasValue(1);
        assertThat(worker.activeCount()).isEqualTo(1);

        worker.signal("done", "same");
        worker.drain();

        assertThat(replacement.state()).isEqualTo(FlowState.FINISHED);
        assertThat(completions).hasValue(2);
        assertThat(worker.activeCount()).isZero();
    }

    @Test
    void stalePredicateFailureCannotFailFinishedFlowAgain() {
        InMemoryEventBus bus = InMemoryEventBus.create();
        List<FlowState> terminalStates = new ArrayList<>();
        EventWorker worker = EventWorker.builder("terminal")
                .clock(new ManualClock())
                .eventBus(bus)
                .listener(new FlowerListener() {
                    @Override
                    public void onFlowFinished(FlowSnapshot flow) {
                        terminalStates.add(flow.state());
                    }

                    @Override
                    public void onFlowFailed(FlowSnapshot flow, Throwable cause) {
                        terminalStates.add(flow.state());
                    }
                })
                .build();
        EventFlow flow = EventFlow.builder("predicate", "terminal")
                .step("wait", predicateStep(EventStepResult.finish()))
                .build();

        worker.submit(flow);
        worker.drain();
        bus.publish("good");
        bus.publish("bad");
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(terminalStates).containsExactly(FlowState.FINISHED);
        assertThat(worker.activeCount()).isZero();
    }

    @Test
    void stalePredicateFailureCannotFailNextStep() {
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = worker("transition", bus);
        EventFlow flow = EventFlow.builder("predicate", "transition")
                .step("first", predicateStep(EventStepResult.next()))
                .step("second", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.signal("done", "second"));
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();
        bus.publish("good");
        bus.publish("bad");
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);
        assertThat(flow.currentStepId()).isEqualTo("second");
        assertThat(worker.activeCount()).isEqualTo(1);
    }

    @Test
    void currentPredicateFailureStillFailsFlow() {
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = worker("current", bus);
        EventFlow flow = EventFlow.builder("predicate", "current")
                .step("wait", predicateStep(EventStepResult.finish()))
                .build();

        worker.submit(flow);
        worker.drain();
        bus.publish("bad");
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FAILED);
        assertThat(flow.failureCause()).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad event");
        assertThat(worker.activeCount()).isZero();
    }

    private static EventWorker worker(String name, InMemoryEventBus bus) {
        return EventWorker.builder(name)
                .clock(new ManualClock())
                .eventBus(bus)
                .build();
    }

    private static EventStep predicateStep(final EventStepResult result) {
        return new EventStep() {
            @Override
            protected EventStepResult onEnter(EventStepContext ctx) {
                return EventStepResult.await(AwaitCondition.event(String.class, value -> {
                    if ("bad".equals(value)) {
                        throw new IllegalArgumentException("bad event");
                    }
                    return true;
                }));
            }

            @Override
            protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                return result;
            }
        };
    }
}
