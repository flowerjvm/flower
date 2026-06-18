package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent-shaped integration scenarios without a real AI provider.
 *
 * <p>The fake services prove the event-loop shape: LLM response event, MCP/tool
 * callback signal, human approval signal, and durable recovery while waiting.
 */
class AgentStyleEventFlowIntegrationTest {

    private static final String FLOW_TYPE = "agent-run";
    private static final String FLOW_KEY = "run-1";
    private static final String TOOL_CALL_ID = "tool-call-1";
    private static final String APPROVAL_ID = "approval-1";

    @Test
    void agentFlowCompletesThroughFakeLlmToolAndHumanSignals() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        AgentEventWorker worker = AgentEventWorker.builder("runtime")
                .clock(clock)
                .eventBus(bus)
                .build();
        List<String> log = new ArrayList<>();

        bus.subscribe(LlmRequested.class, request ->
                bus.publish(new LlmResponded(request.runId, "search docs")));

        EventFlow flow = buildAgentFlow(FLOW_KEY, log, false);

        worker.submit(flow);
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);
        assertThat(log).containsExactly(
                "llm-requested",
                "llm-responded:search docs",
                "tool-requested:" + TOOL_CALL_ID);

        worker.signal("tool-result", TOOL_CALL_ID, "found context");
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.RUNNING);
        assertThat(log).containsExactly(
                "llm-requested",
                "llm-responded:search docs",
                "tool-requested:" + TOOL_CALL_ID,
                "tool-result:found context",
                "approval-requested:" + APPROVAL_ID);

        worker.signal("human-approval", APPROVAL_ID, "approved");
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(worker.activeCount()).isZero();
        assertThat(log).containsExactly(
                "llm-requested",
                "llm-responded:search docs",
                "tool-requested:" + TOOL_CALL_ID,
                "tool-result:found context",
                "approval-requested:" + APPROVAL_ID,
                "approval:approved");
    }

    @Test
    void durableAgentFlowRecoversWhileWaitingForHumanApproval() {
        ManualClock clock = new ManualClock(1_000L);
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        List<String> log = new ArrayList<>();

        bus.subscribe(LlmRequested.class, request ->
                bus.publish(new LlmResponded(request.runId, "call tool")));

        AgentEventWorker firstWorker = AgentEventWorker.builder("runtime")
                .clock(clock)
                .eventBus(bus)
                .checkpointStore(store)
                .build();
        EventFlow flow = buildAgentFlow(FLOW_KEY, log, true);

        firstWorker.submit(flow);
        firstWorker.drain();
        firstWorker.signal("tool-result", TOOL_CALL_ID, "tool ok");
        firstWorker.drain();

        EventFlowCheckpoint approvalCheckpoint = store.find(flow.flowId()).orElseThrow(AssertionError::new);
        assertThat(approvalCheckpoint.currentStepId()).isEqualTo("approval");
        assertThat(approvalCheckpoint.awaits()).hasSize(2);
        assertThat(approvalCheckpoint.awaits().get(0).type()).isEqualTo(EventAwaitCheckpoint.Type.SIGNAL);
        assertThat(approvalCheckpoint.awaits().get(0).signalName()).isEqualTo("human-approval");
        assertThat(approvalCheckpoint.awaits().get(0).signalKey()).isEqualTo(APPROVAL_ID);

        firstWorker.stop();
        assertThat(firstWorker.activeCount()).isZero();
        assertThat(store.find(flow.flowId())).isPresent();

        AgentEventWorker recoveredWorker = AgentEventWorker.builder("runtime")
                .clock(clock)
                .eventBus(bus)
                .checkpointStore(store)
                .build();
        EventFlowFactoryRegistry registry = EventFlowFactoryRegistry.builder()
                .register(FLOW_TYPE, id -> buildAgentFlow(id.flowKey(), log, true))
                .build();

        int recovered = recoveredWorker.recoverActive(registry);
        recoveredWorker.drain();

        assertThat(recovered).isEqualTo(1);
        assertThat(recoveredWorker.activeCount()).isEqualTo(1);
        assertThat(log).containsExactly(
                "llm-requested",
                "llm-responded:call tool",
                "tool-requested:" + TOOL_CALL_ID,
                "tool-result:tool ok",
                "approval-requested:" + APPROVAL_ID,
                "approval-recovered:" + APPROVAL_ID);

        recoveredWorker.signal("human-approval", APPROVAL_ID, "approved after restart");
        recoveredWorker.drain();

        assertThat(recoveredWorker.activeCount()).isZero();
        assertThat(store.find(FlowId.of(FLOW_TYPE, FLOW_KEY))).isEmpty();
        assertThat(log).containsExactly(
                "llm-requested",
                "llm-responded:call tool",
                "tool-requested:" + TOOL_CALL_ID,
                "tool-result:tool ok",
                "approval-requested:" + APPROVAL_ID,
                "approval-recovered:" + APPROVAL_ID,
                "approval:approved after restart");
    }

    private static EventFlow buildAgentFlow(String runId, List<String> log, boolean durable) {
        EventFlowBuilder builder = EventFlow.builder(FLOW_TYPE, runId)
                .definitionVersion("agent-v1")
                .step("llm", new LlmStep(log))
                .step("tool", new ToolStep(log))
                .step("approval", new ApprovalStep(log));
        if (durable) {
            builder.durable();
        }
        return builder.build();
    }

    static final class LlmStep extends EventStep {
        private final List<String> log;

        LlmStep(List<String> log) {
            this.log = log;
        }

        @Override
        protected EventStepResult onEnter(EventStepContext ctx) {
            return EventStepResult.await(
                    AwaitCondition.event(LlmResponded.class),
                    AwaitCondition.deadlineIn(30_000L))
                    .thenRun(context -> log.add("llm-requested"))
                    .thenPublish(new LlmRequested(ctx.flowId().flowKey(), "plan next action"));
        }

        @Override
        protected EventStepResult onEvent(EventStepContext ctx, Object event) {
            LlmResponded response = (LlmResponded) event;
            log.add("llm-responded:" + response.intent);
            return EventStepResult.next();
        }
    }

    static final class ToolStep extends EventStep {
        private final List<String> log;

        ToolStep(List<String> log) {
            this.log = log;
        }

        @Override
        protected EventStepResult onEnter(EventStepContext ctx) {
            return EventStepResult.await(
                    AwaitCondition.signal("tool-result", TOOL_CALL_ID),
                    AwaitCondition.deadlineIn(30_000L))
                    .thenRun(context -> log.add("tool-requested:" + TOOL_CALL_ID));
        }

        @Override
        protected EventStepResult onEvent(EventStepContext ctx, Object event) {
            EventSignal signal = (EventSignal) event;
            log.add("tool-result:" + signal.payload(String.class));
            return EventStepResult.next();
        }
    }

    static final class ApprovalStep extends EventStep {
        private final List<String> log;

        ApprovalStep(List<String> log) {
            this.log = log;
        }

        @Override
        protected EventStepResult onEnter(EventStepContext ctx) {
            return EventStepResult.await(
                    AwaitCondition.signal("human-approval", APPROVAL_ID),
                    AwaitCondition.deadlineIn(86_400_000L))
                    .thenRun(context -> log.add("approval-requested:" + APPROVAL_ID));
        }

        @Override
        protected EventStepResult onRecover(EventStepContext ctx, EventRecoveryContext recovery) {
            EventAwaitCheckpoint signal = null;
            EventAwaitCheckpoint deadline = null;
            for (EventAwaitCheckpoint await : recovery.awaits()) {
                if (await.type() == EventAwaitCheckpoint.Type.SIGNAL) {
                    signal = await;
                } else if (await.type() == EventAwaitCheckpoint.Type.DEADLINE) {
                    deadline = await;
                }
            }
            if (signal == null) {
                return EventStepResult.fail(new IllegalStateException("approval signal await is missing"));
            }
            log.add("approval-recovered:" + signal.signalKey());
            if (deadline == null) {
                return EventStepResult.await(AwaitCondition.signal(signal.signalName(), signal.signalKey()));
            }
            return EventStepResult.await(
                    AwaitCondition.signal(signal.signalName(), signal.signalKey()),
                    AwaitCondition.deadlineIn(recovery.millisUntil(deadline, ctx.now())));
        }

        @Override
        protected EventStepResult onEvent(EventStepContext ctx, Object event) {
            EventSignal signal = (EventSignal) event;
            log.add("approval:" + signal.payload(String.class));
            return EventStepResult.finish();
        }
    }

    static final class LlmRequested {
        final String runId;
        final String prompt;

        LlmRequested(String runId, String prompt) {
            this.runId = runId;
            this.prompt = prompt;
        }
    }

    static final class LlmResponded {
        final String runId;
        final String intent;

        LlmResponded(String runId, String intent) {
            this.runId = runId;
            this.intent = intent;
        }
    }
}
