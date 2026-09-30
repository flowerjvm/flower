package io.github.flowerjvm.flower.eventloop;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowPersistence;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.eventloop.flow.EventFlow;
import io.github.flowerjvm.flower.eventloop.persistence.EventAwaitCheckpoint;
import io.github.flowerjvm.flower.eventloop.persistence.EventFlowCheckpoint;
import io.github.flowerjvm.flower.eventloop.recovery.EventRecoveryContext;
import io.github.flowerjvm.flower.eventloop.step.AwaitCondition;
import io.github.flowerjvm.flower.eventloop.step.EventStep;
import io.github.flowerjvm.flower.eventloop.step.EventStepContext;
import io.github.flowerjvm.flower.eventloop.step.EventStepResult;
import io.github.flowerjvm.flower.eventloop.worker.EventWorker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EventWorkerPendingRecoveryShutdownTest {

    static final class Response {
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void stoppingBeforeFirstRecoveryDrainPreservesCheckpoint(boolean previouslyEntered) {
        ManualClock clock = new ManualClock(2_000L);
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        FlowId flowId = FlowId.of("recover", "stop-before-drain");
        ExecutionContext identity = ExecutionContext.builder()
                .tenantId("tenant-a")
                .userId("user-a")
                .sessionId("session-a")
                .runId("run-a")
                .traceId("trace-a")
                .correlationId("correlation-a")
                .build();
        List<EventAwaitCheckpoint> awaits = Arrays.asList(
                EventAwaitCheckpoint.event(Response.class.getName()),
                EventAwaitCheckpoint.signal("complete", "request-a"),
                EventAwaitCheckpoint.deadline(5_000L));
        EventFlowCheckpoint original = new EventFlowCheckpoint(
                flowId, FlowState.RUNNING, "wait", previouslyEntered,
                FlowPersistence.DURABLE, "previous-worker", 1_000L,
                "v1", identity, 7L, awaits);
        store.save(original);
        AtomicInteger enters = new AtomicInteger();
        AtomicInteger recoveries = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        AtomicInteger exits = new AtomicInteger();

        EventWorker stoppingWorker = worker("stopping-worker", clock, store);
        stoppingWorker.submit(flow(flowId, identity, original, enters, recoveries, completions, exits)
                .recoverFrom(original));
        stoppingWorker.stop();

        assertThat(enters.get()).isZero();
        assertThat(recoveries.get()).isZero();
        assertThat(exits.get()).isZero();
        EventFlowCheckpoint preserved = store.find(flowId).orElseThrow(AssertionError::new);
        assertThat(preserved.state()).isEqualTo(FlowState.RUNNING);
        assertThat(preserved.currentStepId()).isEqualTo("wait");
        assertThat(preserved.currentStepEntered()).isEqualTo(previouslyEntered);
        assertThat(preserved.definitionVersion()).isEqualTo("v1");
        assertThat(preserved.awaitGeneration()).isEqualTo(7L);
        assertThat(preserved.awaits()).containsExactlyElementsOf(awaits);
        assertIdentity(preserved.executionContext(), identity);
        assertThat(store.findActive()).containsExactly(preserved);
        assertThat(store.findActiveByWorker("stopping-worker")).containsExactly(preserved);

        EventWorker resumedWorker = worker("resumed-worker", clock, store);
        EventFlow resumed = flow(flowId, identity, original, enters, recoveries, completions, exits)
                .recoverFrom(preserved);
        resumedWorker.submit(resumed);
        resumedWorker.drain();

        assertIdentity(resumed.snapshot().executionContext(), identity);
        assertThat(resumed.state()).isEqualTo(FlowState.RUNNING);
        assertThat(enters.get()).isZero();
        assertThat(recoveries.get()).isEqualTo(1);
        EventFlowCheckpoint active = store.find(flowId).orElseThrow(AssertionError::new);
        assertIdentity(active.executionContext(), identity);
        assertThat(active.awaits()).containsExactlyElementsOf(awaits);
        assertThat(active.awaitGeneration()).isEqualTo(8L);

        resumedWorker.signal("complete", "request-a");
        resumedWorker.signal("complete", "request-a");
        resumedWorker.drain();

        assertThat(resumed.state()).isEqualTo(FlowState.FINISHED);
        assertIdentity(resumed.snapshot().executionContext(), identity);
        assertThat(completions.get()).isEqualTo(1);
        assertThat(exits.get()).isEqualTo(1);
        assertThat(store.find(flowId)).isEmpty();
        List<EventFlowCheckpoint> saves = store.saves();
        EventFlowCheckpoint terminal = saves.get(saves.size() - 1);
        assertThat(terminal.state()).isEqualTo(FlowState.FINISHED);
        assertIdentity(terminal.executionContext(), identity);
        resumedWorker.stop();
    }

    private static EventWorker worker(
            String name, ManualClock clock, FakeEventFlowCheckpointStore store) {
        return EventWorker.builder(name)
                .clock(clock)
                .eventBus(InMemoryEventBus.create())
                .checkpointStore(store)
                .build();
    }

    private static EventFlow flow(
            FlowId id,
            ExecutionContext identity,
            EventFlowCheckpoint original,
            AtomicInteger enters,
            AtomicInteger recoveries,
            AtomicInteger completions,
            AtomicInteger exits) {
        return EventFlow.builder(id.flowType(), id.flowKey())
                .durable()
                .definitionVersion("v1")
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        enters.incrementAndGet();
                        return EventStepResult.finish();
                    }

                    @Override
                    protected EventStepResult onRecover(
                            EventStepContext ctx, EventRecoveryContext recovery) {
                        recoveries.incrementAndGet();
                        assertIdentity(ctx.executionContext(), identity);
                        assertThat(recovery.checkpoint().currentStepEntered())
                                .isEqualTo(original.currentStepEntered());
                        assertThat(recovery.awaitGeneration()).isEqualTo(original.awaitGeneration());
                        assertThat(recovery.awaits()).containsExactlyElementsOf(original.awaits());
                        return EventStepResult.await(
                                AwaitCondition.event(Response.class),
                                AwaitCondition.signal("complete", "request-a"),
                                AwaitCondition.deadlineIn(recovery.millisUntil(
                                        original.awaits().get(2), ctx.now())));
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        assertIdentity(ctx.executionContext(), identity);
                        completions.incrementAndGet();
                        return EventStepResult.finish();
                    }

                    @Override
                    protected void onExit(EventStepContext ctx) {
                        exits.incrementAndGet();
                    }
                })
                .build();
    }

    private static void assertIdentity(ExecutionContext actual, ExecutionContext expected) {
        assertThat(actual.tenantId()).isEqualTo(expected.tenantId());
        assertThat(actual.userId()).isEqualTo(expected.userId());
        assertThat(actual.sessionId()).isEqualTo(expected.sessionId());
        assertThat(actual.runId()).isEqualTo(expected.runId());
        assertThat(actual.traceId()).isEqualTo(expected.traceId());
        assertThat(actual.correlationId()).isEqualTo(expected.correlationId());
    }
}
