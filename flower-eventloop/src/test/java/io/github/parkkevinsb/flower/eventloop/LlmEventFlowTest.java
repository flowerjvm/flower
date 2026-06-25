package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.eventloop.flow.EventFlow;
import io.github.parkkevinsb.flower.eventloop.step.AwaitCondition;
import io.github.parkkevinsb.flower.eventloop.step.EventStep;
import io.github.parkkevinsb.flower.eventloop.step.EventStepContext;
import io.github.parkkevinsb.flower.eventloop.step.EventStepResult;
import io.github.parkkevinsb.flower.eventloop.worker.EventWorker;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic walk-through of an LLM-style event flow:
 *
 * <pre>
 * request  -> publish LLM request, await response event OR 30s deadline
 * complete -> finish on response
 * fallback -> finish on timeout
 * </pre>
 *
 * Drives the worker with {@link ManualClock} and {@link EventWorker#drain()},
 * mirroring how core flows are tested with {@code ManualClock} + {@code tickOnce()}.
 */
class LlmEventFlowTest {

    // --- events ---
    static final class LlmRequested {
        final String prompt;
        LlmRequested(String prompt) { this.prompt = prompt; }
    }

    static final class LlmResponded {
        final String text;
        LlmResponded(String text) { this.text = text; }
    }

    // --- steps ---
    static final class RequestStep extends EventStep {
        @Override
        protected EventStepResult onEnter(EventStepContext ctx) {
            return EventStepResult.await(
                    AwaitCondition.event(LlmResponded.class),
                    AwaitCondition.deadlineIn(30_000))
                    .thenPublish(new LlmRequested("hello"));
        }

        @Override
        protected EventStepResult onEvent(EventStepContext ctx, Object event) {
            if (event instanceof LlmResponded) {
                return EventStepResult.next();
            }
            return null;
        }

        @Override
        protected EventStepResult onTimeout(EventStepContext ctx) {
            return EventStepResult.goTo("fallback");
        }
    }

    static final class RecordingStep extends EventStep {
        private final List<String> log;
        private final String label;
        RecordingStep(List<String> log, String label) { this.log = log; this.label = label; }

        @Override
        protected EventStepResult onEnter(EventStepContext ctx) {
            log.add(label);
            return EventStepResult.finish();
        }
    }

    private EventFlow buildFlow(List<String> log) {
        return EventFlow.builder("llm-call", "req-1")
                .step("request", new RequestStep())
                .step("complete", new RecordingStep(log, "complete"))
                .step("fallback", new RecordingStep(log, "fallback"))
                .build();
    }

    @Test
    void completesWhenResponseArrivesBeforeDeadline() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = EventWorker.builder("llm").clock(clock).eventBus(bus).build();
        List<String> log = new ArrayList<>();

        FlowId id = FlowId.of("llm-call", "req-1");
        worker.submit(buildFlow(log));
        worker.drain();

        // The request step is now awaiting; nothing has completed yet.
        assertThat(log).isEmpty();
        assertThat(worker.stateOf(id)).isEqualTo(FlowState.RUNNING);

        // Response arrives well before the 30s deadline.
        bus.publish(new LlmResponded("hi there"));
        worker.drain();

        assertThat(log).containsExactly("complete");
        assertThat(worker.stateOf(id)).isNull(); // removed after finishing
    }

    @Test
    void doesNotLoseSynchronousResponsePublishedDuringRequestEffect() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = EventWorker.builder("llm").clock(clock).eventBus(bus).build();
        List<String> log = new ArrayList<>();

        bus.subscribe(LlmRequested.class, request -> bus.publish(new LlmResponded("sync")));

        worker.submit(buildFlow(log));
        worker.drain();

        assertThat(log).containsExactly("complete");
    }

    @Test
    void routesToFallbackWhenDeadlineElapses() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = EventWorker.builder("llm").clock(clock).eventBus(bus).build();
        List<String> log = new ArrayList<>();

        worker.submit(buildFlow(log));
        worker.drain();
        assertThat(log).isEmpty();

        // No response; advance past the deadline and let the worker time out.
        clock.advance(30_000);
        worker.drain();

        assertThat(log).containsExactly("fallback");
    }

    @Test
    void lateResponseAfterTimeoutIsIgnored() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        EventWorker worker = EventWorker.builder("llm").clock(clock).eventBus(bus).build();
        List<String> log = new ArrayList<>();

        worker.submit(buildFlow(log));
        worker.drain();

        clock.advance(30_000);
        worker.drain();
        assertThat(log).containsExactly("fallback");

        // A response that arrives after the flow finished must not re-trigger anything.
        bus.publish(new LlmResponded("too late"));
        worker.drain();
        assertThat(log).containsExactly("fallback");
    }
}
