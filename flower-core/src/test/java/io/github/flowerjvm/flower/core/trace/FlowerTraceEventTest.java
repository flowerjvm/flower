package io.github.flowerjvm.flower.core.trace;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import io.github.flowerjvm.flower.core.step.GuardResult;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerTraceEventTest {

    @Test
    void trace_reconstructs_the_effective_flow_path() {
        TraceFixture fixture = fixture();
        Flow flow = Flow.builder("maintenance", "job-1")
                .executionContext(context())
                .step("classify", stepReturning(StepResult.goTo("report")))
                .step("investigate", stepReturning(StepResult.fail(new AssertionError("must be skipped"))))
                .step("report", stepReturning(StepResult.done()))
                .build();

        fixture.worker.submit(flow);
        fixture.worker.tickOnce();
        fixture.worker.tickOnce();

        assertThat(types(fixture.events)).containsExactly(
                FlowerTraceEventType.FLOW_STARTED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.STEP_COMPLETED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.STEP_COMPLETED,
                FlowerTraceEventType.FLOW_COMPLETED);
        assertThat(fixture.events).extracting(FlowerTraceEvent::sequence)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(fixture.events).extracting(FlowerTraceEvent::eventId)
                .containsExactly(
                        "run-1:event:1",
                        "run-1:event:2",
                        "run-1:event:3",
                        "run-1:event:4",
                        "run-1:event:5",
                        "run-1:event:6");
        assertThat(fixture.events).allSatisfy(event -> {
            assertThat(event.schemaVersion()).isEqualTo(1);
            assertThat(event.source()).isEqualTo("flower-core");
            assertThat(event.traceId()).isEqualTo("trace-1");
            assertThat(event.flowRunId()).isEqualTo("run-1");
            assertThat(event.occurredAt()).isEqualTo(Instant.ofEpochMilli(1_000));
        });

        FlowerTraceEvent classifyStart = fixture.events.get(1);
        FlowerTraceEvent classifyCompleted = fixture.events.get(2);
        assertThat(classifyStart.stepRunId()).isEqualTo("run-1:step:1");
        assertThat(classifyCompleted.stepRunId()).isEqualTo(classifyStart.stepRunId());
        assertThat(classifyCompleted.parentRunId()).isEqualTo("run-1");
        assertThat(classifyCompleted.attributes())
                .containsEntry(FlowerTraceAttributes.STEP_ID, "classify")
                .containsEntry(FlowerTraceAttributes.STEP_OUTCOME, "GOTO")
                .containsEntry(FlowerTraceAttributes.STEP_TARGET_ID, "report");

        FlowerTraceEvent reportCompleted = fixture.events.get(4);
        assertThat(reportCompleted.stepRunId()).isEqualTo("run-1:step:2");
        assertThat(reportCompleted.attributes())
                .containsEntry(FlowerTraceAttributes.STEP_ID, "report")
                .containsEntry(FlowerTraceAttributes.STEP_OUTCOME, "DONE");
    }

    @Test
    void repeat_creates_a_new_step_run() {
        TraceFixture fixture = fixture();
        Step repeating = new Step() {
            private int attempt;

            @Override
            protected StepResult onTick(StepContext ctx) {
                return ++attempt == 1 ? StepResult.repeat() : StepResult.done();
            }
        };
        Flow flow = Flow.builder("maintenance", "job-1")
                .executionContext(context())
                .step("retryable", repeating)
                .build();

        fixture.worker.submit(flow);
        fixture.worker.tickOnce();
        fixture.worker.tickOnce();

        assertThat(fixture.events.get(1).stepRunId()).isEqualTo("run-1:step:1");
        assertThat(fixture.events.get(2).stepRunId()).isEqualTo("run-1:step:1");
        assertThat(fixture.events.get(2).attributes())
                .containsEntry(FlowerTraceAttributes.STEP_OUTCOME, "REPEAT")
                .containsEntry(FlowerTraceAttributes.STEP_TARGET_ID, "retryable");
        assertThat(fixture.events.get(3).stepRunId()).isEqualTo("run-1:step:2");
        assertThat(fixture.events.get(4).stepRunId()).isEqualTo("run-1:step:2");
    }

    @Test
    void lifecycle_failure_and_external_cancel_have_explicit_events() {
        TraceFixture failed = fixture();
        Step enterFailure = new Step() {
            @Override
            protected void onEnter(StepContext ctx) {
                throw new IllegalStateException("cannot enter");
            }

            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.done();
            }
        };
        Flow failedFlow = Flow.builder("maintenance", "failed")
                .executionContext(context())
                .step("execute", enterFailure)
                .build();
        failed.worker.submit(failedFlow);
        failed.worker.tickOnce();

        assertThat(types(failed.events)).containsExactly(
                FlowerTraceEventType.FLOW_STARTED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.STEP_FAILED,
                FlowerTraceEventType.FLOW_FAILED);
        assertThat(failed.events.get(2).attributes())
                .containsEntry(FlowerTraceAttributes.STEP_TRANSITION_ORIGIN, "LIFECYCLE")
                .containsEntry(FlowerTraceAttributes.ERROR_TYPE, IllegalStateException.class.getName())
                .containsEntry(FlowerTraceAttributes.ERROR_MESSAGE, "cannot enter");

        TraceFixture cancelled = fixture();
        Flow cancelledFlow = Flow.builder("maintenance", "cancelled")
                .executionContext(context())
                .step("wait", stepReturning(StepResult.stay()))
                .build();
        cancelled.worker.submit(cancelledFlow);
        cancelled.worker.tickOnce();
        cancelled.worker.cancel(cancelledFlow.flowId());
        cancelled.worker.tickOnce();

        assertThat(types(cancelled.events)).containsExactly(
                FlowerTraceEventType.FLOW_STARTED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.STEP_CANCELLED,
                FlowerTraceEventType.FLOW_CANCELLED);
        assertThat(cancelled.events.get(2).attributes())
                .containsEntry(FlowerTraceAttributes.STEP_TRANSITION_ORIGIN, "EXTERNAL")
                .containsEntry(FlowerTraceAttributes.STEP_OUTCOME, "CANCELLED");
    }

    @Test
    void guard_redirect_before_entry_is_reported_as_skipped() {
        TraceFixture fixture = fixture();
        Flow flow = Flow.builder("maintenance", "job-1")
                .executionContext(context())
                .step("approval", stepReturning(StepResult.done()), ctx -> GuardResult.goTo("report"))
                .step("report", stepReturning(StepResult.done()))
                .build();

        fixture.worker.submit(flow);
        fixture.worker.tickOnce();
        fixture.worker.tickOnce();

        assertThat(types(fixture.events)).containsExactly(
                FlowerTraceEventType.FLOW_STARTED,
                FlowerTraceEventType.STEP_SKIPPED,
                FlowerTraceEventType.STEP_STARTED,
                FlowerTraceEventType.STEP_COMPLETED,
                FlowerTraceEventType.FLOW_COMPLETED);
        FlowerTraceEvent skipped = fixture.events.get(1);
        assertThat(skipped.stepRunId()).isNull();
        assertThat(skipped.attributes())
                .containsEntry(FlowerTraceAttributes.STEP_ID, "approval")
                .containsEntry(FlowerTraceAttributes.STEP_TRANSITION_ORIGIN, "GUARD")
                .containsEntry(FlowerTraceAttributes.STEP_OUTCOME, "GOTO")
                .containsEntry(FlowerTraceAttributes.STEP_TARGET_ID, "report");
    }

    @Test
    void trace_listener_failure_cannot_change_flow_execution() {
        List<String> listenerErrors = new ArrayList<>();
        Worker worker = Worker.builder("trace-test").build();
        Engine engine = Engine.builder()
                .clock(new ManualClock(1_000))
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .listener(new FlowerTraceListener() {
                    @Override
                    public void onTraceEvent(FlowerTraceEvent event) {
                        throw new IllegalStateException("trace sink down");
                    }
                })
                .listener(new FlowerListener() {
                    @Override
                    public void onListenerError(FlowSnapshot flow, String callbackName, Throwable cause) {
                        listenerErrors.add(callbackName + ":" + cause.getMessage());
                    }
                })
                .build();
        engine.attach();
        Flow flow = Flow.builder("maintenance", "job-1")
                .executionContext(context())
                .step("report", stepReturning(StepResult.done()))
                .build();

        worker.submit(flow);
        worker.tickOnce();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(listenerErrors).containsExactly(
                "onTraceEvent:trace sink down",
                "onTraceEvent:trace sink down",
                "onTraceEvent:trace sink down",
                "onTraceEvent:trace sink down");
    }

    private static TraceFixture fixture() {
        List<FlowerTraceEvent> events = new ArrayList<>();
        Worker worker = Worker.builder("trace-test").build();
        Engine engine = Engine.builder()
                .clock(new ManualClock(1_000))
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .listener(new FlowerTraceListener() {
                    @Override
                    public void onTraceEvent(FlowerTraceEvent event) {
                        events.add(event);
                    }
                })
                .build();
        engine.attach();
        return new TraceFixture(worker, events);
    }

    private static ExecutionContext context() {
        return ExecutionContext.builder()
                .runId("run-1")
                .traceId("trace-1")
                .correlationId("task-1")
                .build();
    }

    private static Step stepReturning(StepResult result) {
        return new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return result;
            }
        };
    }

    private static List<FlowerTraceEventType> types(List<FlowerTraceEvent> events) {
        List<FlowerTraceEventType> types = new ArrayList<>();
        for (FlowerTraceEvent event : events) {
            types.add(event.type());
        }
        return types;
    }

    private static final class TraceFixture {
        private final Worker worker;
        private final List<FlowerTraceEvent> events;

        private TraceFixture(Worker worker, List<FlowerTraceEvent> events) {
            this.worker = worker;
            this.events = events;
        }
    }
}
