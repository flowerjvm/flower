package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecializedEventWorkerTest {

    @Test
    void llmWorkerUsesPrefixedNameAndDelegatesLifecycle() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        LlmEventWorker worker = LlmEventWorker.builder("primary")
                .clock(clock)
                .eventBus(bus)
                .build();

        EventFlow flow = EventFlow.builder("llm", "req-1")
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.signal("llm-response", "req-1"));
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        assertThat(worker.role()).isEqualTo("llm");
        assertThat(worker.name()).isEqualTo("llm-primary");
        assertThat(worker.delegate().name()).isEqualTo("llm-primary");

        worker.submit(flow);
        worker.drain();
        assertThat(worker.activeCount()).isEqualTo(1);

        worker.signal("llm-response", "req-1");
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(worker.activeCount()).isZero();
    }

    @Test
    void builderCanOverrideWorkerNameForCheckpointCompatibility() {
        AgentEventWorker worker = AgentEventWorker.builder("runtime")
                .workerName("archdox-agent")
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .build();

        assertThat(worker.role()).isEqualTo("agent");
        assertThat(worker.name()).isEqualTo("archdox-agent");
    }

    @Test
    void builderRequiresEventBus() {
        assertThatThrownBy(() -> McpEventWorker.builder("tools").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EventBus");
    }

    @Test
    void specializedWorkerRecoversActiveDurableFlowsForOwnWorker() {
        ManualClock clock = new ManualClock(1_000L);
        InMemoryEventBus bus = InMemoryEventBus.create();
        FakeEventFlowCheckpointStore store = new FakeEventFlowCheckpointStore();
        FlowId flowId = FlowId.of("agent", "run-1");

        store.save(checkpoint(flowId, "agent-runtime",
                EventAwaitCheckpoint.signal("approval", "approve-1")));

        AgentEventWorker worker = AgentEventWorker.builder("runtime")
                .clock(clock)
                .eventBus(bus)
                .checkpointStore(store)
                .build();

        EventFlowFactoryRegistry registry = EventFlowFactoryRegistry.builder()
                .register("agent", id -> EventFlow.builder(id.flowType(), id.flowKey())
                        .durable()
                        .definitionVersion("v1")
                        .step("wait", new EventStep() {
                            @Override
                            protected EventStepResult onEnter(EventStepContext ctx) {
                                return EventStepResult.await(
                                        AwaitCondition.signal("approval", "approve-1"));
                            }

                            @Override
                            protected EventStepResult onRecover(
                                    EventStepContext ctx,
                                    EventRecoveryContext recovery) {
                                EventAwaitCheckpoint await = recovery.awaits().get(0);
                                return EventStepResult.await(
                                        AwaitCondition.signal(await.signalName(), await.signalKey()));
                            }

                            @Override
                            protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                                return EventStepResult.finish();
                            }
                        })
                        .build())
                .build();

        int recovered = worker.recoverActive(registry);
        worker.drain();

        assertThat(recovered).isEqualTo(1);
        assertThat(worker.activeCount()).isEqualTo(1);

        worker.signal("approval", "approve-1");
        worker.drain();

        assertThat(worker.activeCount()).isZero();
        assertThat(store.find(flowId)).isEmpty();
    }

    @Test
    void mcpWorkerCarriesOffloadExecutor() {
        ManualClock clock = new ManualClock();
        InMemoryEventBus bus = InMemoryEventBus.create();
        AtomicInteger offloads = new AtomicInteger();
        Executor directExecutor = command -> {
            offloads.incrementAndGet();
            command.run();
        };
        McpEventWorker worker = McpEventWorker.builder("tools")
                .clock(clock)
                .eventBus(bus)
                .offloadExecutor(directExecutor)
                .build();

        EventFlow flow = EventFlow.builder("mcp", "tool-1")
                .step("call", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.signal("tool-result", "tool-1"))
                                .thenRunOffloaded(context -> context.signal("tool-result", "tool-1"));
                    }

                    @Override
                    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        assertThat(offloads.get()).isEqualTo(1);
        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
    }

    private static EventFlowCheckpoint checkpoint(
            FlowId flowId,
            String workerName,
            EventAwaitCheckpoint await) {
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
                3L,
                Collections.singletonList(await));
    }
}
