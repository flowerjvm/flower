package io.github.flowerjvm.flower.eventloop;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.trace.FlowerTraceAttributes;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEventType;
import io.github.flowerjvm.flower.core.trace.FlowerTraceListener;
import io.github.flowerjvm.flower.eventloop.flow.EventFlow;
import io.github.flowerjvm.flower.eventloop.persistence.EventFlowCheckpoint;
import io.github.flowerjvm.flower.eventloop.recovery.EventRecoveryContext;
import io.github.flowerjvm.flower.eventloop.step.AwaitCondition;
import io.github.flowerjvm.flower.eventloop.step.EventStep;
import io.github.flowerjvm.flower.eventloop.step.EventStepContext;
import io.github.flowerjvm.flower.eventloop.step.EventStepResult;
import io.github.flowerjvm.flower.eventloop.worker.EventWorker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventWorkerTraceTest {

    static final class Response {
    }

    @Test
    void signal_wait_and_resume_share_one_step_run() {
        ManualClock clock = new ManualClock(1_000L);
        RecordingTrace trace = new RecordingTrace();
        EventWorker worker = worker("trace-signal", clock, new FakeEventFlowCheckpointStore(), trace);
        EventFlow flow = EventFlow.builder("agent", "signal")
                .executionContext(context())
                .step("call-tool", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(
                                AwaitCondition.signal("tool-call", "call-1"),
                                AwaitCondition.deadlineIn(500L));
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        assertThat(types(trace.events)).containsExactly(
                FlowerTraceEventType.FLOW_STARTED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.FLOW_WAITING);
        FlowerTraceEvent waiting = trace.events.get(2);
        assertThat(waiting.attributes())
                .containsEntry(FlowerTraceAttributes.WAIT_GENERATION, 1L);
        List<Map<String, Object>> conditions = conditions(waiting);
        assertThat(conditions).hasSize(2);
        assertThat(conditions.get(0))
                .containsEntry(FlowerTraceAttributes.WAIT_CONDITION_TYPE, "SIGNAL")
                .containsEntry(FlowerTraceAttributes.WAIT_SIGNAL_NAME, "tool-call")
                .containsEntry(FlowerTraceAttributes.WAIT_SIGNAL_KEY, "call-1");
        assertThat(conditions.get(1))
                .containsEntry(FlowerTraceAttributes.WAIT_CONDITION_TYPE, "DEADLINE")
                .containsEntry(FlowerTraceAttributes.WAIT_DEADLINE_AT_MILLIS, 1_500L);

        worker.signal("tool-call", "call-1", "secret payload");
        worker.drain();

        assertThat(types(trace.events)).containsExactly(
                FlowerTraceEventType.FLOW_STARTED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.FLOW_WAITING,
                FlowerTraceEventType.FLOW_RESUMED,
                FlowerTraceEventType.STEP_COMPLETED,
                FlowerTraceEventType.FLOW_COMPLETED);
        FlowerTraceEvent resumed = trace.events.get(3);
        assertThat(resumed.attributes())
                .containsEntry(FlowerTraceAttributes.RESUME_REASON, "SIGNAL")
                .containsEntry(FlowerTraceAttributes.RESUME_SIGNAL_NAME, "tool-call")
                .containsEntry(FlowerTraceAttributes.RESUME_SIGNAL_KEY, "call-1")
                .doesNotContainValue("secret payload");
        assertThat(trace.events.subList(1, 5))
                .extracting(FlowerTraceEvent::stepRunId)
                .containsOnly(trace.events.get(1).stepRunId());
        assertThat(trace.events)
                .extracting(FlowerTraceEvent::sequence)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void timeout_resume_can_return_to_waiting_on_remaining_event() {
        ManualClock clock = new ManualClock(2_000L);
        InMemoryEventBus bus = InMemoryEventBus.create();
        RecordingTrace trace = new RecordingTrace();
        EventWorker worker = EventWorker.builder("trace-timeout")
                .clock(clock)
                .eventBus(bus)
                .listener(trace)
                .build();
        EventFlow flow = EventFlow.builder("agent", "timeout")
                .executionContext(context())
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(
                                AwaitCondition.event(Response.class),
                                AwaitCondition.deadlineIn(100L));
                    }

                    @Override
                    protected EventStepResult onTimeout(EventStepContext ctx) {
                        return null;
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();
        clock.advance(100L);
        worker.drain();

        assertThat(types(trace.events)).containsExactly(
                FlowerTraceEventType.FLOW_STARTED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.FLOW_WAITING,
                FlowerTraceEventType.FLOW_RESUMED,
                FlowerTraceEventType.FLOW_WAITING);
        assertThat(trace.events.get(3).attributes())
                .containsEntry(FlowerTraceAttributes.RESUME_REASON, "TIMEOUT")
                .containsEntry(FlowerTraceAttributes.RESUME_DEADLINE_AT_MILLIS, 2_100L);
        assertThat(conditions(trace.events.get(4))).singleElement()
                .satisfies(condition -> assertThat(condition)
                        .containsEntry(FlowerTraceAttributes.WAIT_CONDITION_TYPE, "EVENT")
                        .containsEntry(FlowerTraceAttributes.WAIT_EVENT_TYPE, Response.class.getName()));

        bus.publish(new Response());
        worker.drain();
        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(types(trace.events)).endsWith(
                FlowerTraceEventType.FLOW_RESUMED,
                FlowerTraceEventType.STEP_COMPLETED,
                FlowerTraceEventType.FLOW_COMPLETED);
    }

    @Test
    void durable_recovery_preserves_logical_trace_and_starts_a_new_runtime_segment() {
        ManualClock clock = new ManualClock(3_000L);
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        RecordingTrace originalTrace = new RecordingTrace();
        EventWorker originalWorker = worker("worker-a", clock, store, originalTrace);
        EventFlow original = EventFlow.builder("agent", "durable")
                .durable()
                .definitionVersion("v1")
                .step("wait", new AwaitingStep())
                .build();

        originalWorker.submit(original);
        originalWorker.drain();
        EventFlowCheckpoint checkpoint = store.find(original.flowId()).orElseThrow(AssertionError::new);

        assertThat(checkpoint.executionContext().runIdOrNull()).isNotBlank();
        assertThat(checkpoint.executionContext().traceIdOrNull())
                .isEqualTo(checkpoint.executionContext().runIdOrNull());
        String flowRunId = originalTrace.events.get(0).flowRunId();
        String originalRuntimeId = (String) originalTrace.events.get(0).attributes()
                .get(FlowerTraceAttributes.FLOW_RUNTIME_ID);
        assertThat(checkpoint.executionContext().runIdOrNull()).isEqualTo(flowRunId);

        originalWorker.stop();
        assertThat(types(originalTrace.events)).endsWith(FlowerTraceEventType.FLOW_SUSPENDED);

        RecordingTrace recoveredTrace = new RecordingTrace();
        EventWorker recoveredWorker = worker("worker-b", clock, store, recoveredTrace);
        EventFlow recovered = EventFlow.builder("agent", "durable")
                .durable()
                .definitionVersion("v1")
                .step("wait", new AwaitingStep())
                .build()
                .recoverFrom(checkpoint);

        recoveredWorker.submit(recovered);
        recoveredWorker.drain();

        assertThat(types(recoveredTrace.events)).containsExactly(
                FlowerTraceEventType.FLOW_RECOVERED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.FLOW_RESUMED,
                FlowerTraceEventType.CHECKPOINT_SAVED,
                FlowerTraceEventType.FLOW_WAITING);
        assertThat(recoveredTrace.events).allSatisfy(event -> {
            assertThat(event.flowRunId()).isEqualTo(flowRunId);
            assertThat(event.traceId()).isEqualTo(checkpoint.executionContext().traceIdOrNull());
        });
        String recoveredRuntimeId = (String) recoveredTrace.events.get(0).attributes()
                .get(FlowerTraceAttributes.FLOW_RUNTIME_ID);
        assertThat(recoveredRuntimeId).isNotEqualTo(originalRuntimeId);
        assertThat(recoveredTrace.events.get(2).attributes())
                .containsEntry(FlowerTraceAttributes.RESUME_REASON, "RECOVERY");

        recoveredWorker.signal("tool-call", "call-1");
        recoveredWorker.drain();

        assertThat(recovered.state()).isEqualTo(FlowState.FINISHED);
        assertThat(types(recoveredTrace.events)).endsWith(
                FlowerTraceEventType.FLOW_RESUMED,
                FlowerTraceEventType.STEP_COMPLETED,
                FlowerTraceEventType.CHECKPOINT_SAVED,
                FlowerTraceEventType.FLOW_COMPLETED);
    }

    @Test
    void checkpoint_failure_is_visible_and_never_reports_waiting() {
        ManualClock clock = new ManualClock(4_000L);
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        store.failSavesWith(new IllegalStateException("checkpoint down"));
        RecordingTrace trace = new RecordingTrace();
        EventWorker worker = worker("trace-failure", clock, store, trace);
        EventFlow flow = EventFlow.builder("agent", "checkpoint-failure")
                .durable()
                .step("wait", new AwaitingStep())
                .build();

        worker.submit(flow);
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.CHECKPOINT_FAILED);
        assertThat(types(trace.events)).containsExactly(
                FlowerTraceEventType.FLOW_STARTED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.CHECKPOINT_FAILED,
                FlowerTraceEventType.FLOW_FAILED);
        assertThat(trace.events.get(2).attributes())
                .containsEntry(FlowerTraceAttributes.ERROR_MESSAGE, "checkpoint down");
        assertThat(types(trace.events)).doesNotContain(FlowerTraceEventType.FLOW_WAITING);
    }

    @Test
    void external_cancel_closes_the_waiting_step_without_a_fake_resume() {
        ManualClock clock = new ManualClock(5_000L);
        RecordingTrace trace = new RecordingTrace();
        EventWorker worker = worker("trace-cancel", clock, new FakeEventFlowCheckpointStore(), trace);
        EventFlow flow = EventFlow.builder("agent", "cancel")
                .executionContext(context())
                .step("wait", new AwaitingStep())
                .build();

        worker.submit(flow);
        worker.drain();
        worker.cancel(flow.flowId());
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(types(trace.events)).containsExactly(
                FlowerTraceEventType.FLOW_STARTED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.FLOW_WAITING,
                FlowerTraceEventType.STEP_CANCELLED,
                FlowerTraceEventType.FLOW_CANCELLED);
        assertThat(types(trace.events)).doesNotContain(FlowerTraceEventType.FLOW_RESUMED);
    }

    @Test
    void cancel_before_first_entry_still_has_a_complete_flow_trace() {
        ManualClock clock = new ManualClock(6_000L);
        RecordingTrace trace = new RecordingTrace();
        EventWorker worker = worker("trace-early-cancel", clock,
                new FakeEventFlowCheckpointStore(), trace);
        EventFlow flow = EventFlow.builder("agent", "early-cancel")
                .executionContext(context())
                .step("wait", new AwaitingStep())
                .build();

        worker.submit(flow);
        worker.cancel(flow.flowId());
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(types(trace.events)).containsExactly(
                FlowerTraceEventType.FLOW_STARTED,
                FlowerTraceEventType.FLOW_CANCELLED);
    }

    private static EventWorker worker(
            String name,
            ManualClock clock,
            FakeEventFlowCheckpointStore store,
            RecordingTrace trace) {
        return EventWorker.builder(name)
                .clock(clock)
                .eventBus(InMemoryEventBus.create())
                .checkpointStore(store)
                .listener(trace)
                .build();
    }

    private static ExecutionContext context() {
        return ExecutionContext.builder()
                .runId("run-1")
                .traceId("trace-1")
                .correlationId("task-1")
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> conditions(FlowerTraceEvent event) {
        return (List<Map<String, Object>>) event.attributes()
                .get(FlowerTraceAttributes.WAIT_CONDITIONS);
    }

    private static List<FlowerTraceEventType> types(List<FlowerTraceEvent> events) {
        List<FlowerTraceEventType> types = new ArrayList<>();
        for (FlowerTraceEvent event : events) {
            types.add(event.type());
        }
        return types;
    }

    private static final class RecordingTrace implements FlowerTraceListener {
        private final List<FlowerTraceEvent> events = new ArrayList<>();

        @Override
        public void onTraceEvent(FlowerTraceEvent event) {
            events.add(event);
        }
    }

    private static final class AwaitingStep extends EventStep {
        @Override
        protected EventStepResult onEnter(EventStepContext ctx) {
            return EventStepResult.await(AwaitCondition.signal("tool-call", "call-1"));
        }

        @Override
        protected EventStepResult onRecover(
                EventStepContext ctx,
                EventRecoveryContext recovery) {
            return EventStepResult.await(AwaitCondition.signal("tool-call", "call-1"));
        }

        @Override
        protected EventStepResult onEvent(EventStepContext ctx, Object event) {
            return EventStepResult.finish();
        }
    }
}
