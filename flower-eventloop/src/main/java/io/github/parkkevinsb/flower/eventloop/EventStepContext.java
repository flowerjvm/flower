package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.time.Clock;

/**
 * Handle passed into every {@link EventStep} lifecycle method.
 *
 * <p>It exposes flow identity, the shared {@link EventBus} for publishing
 * outbound events, and the {@link Clock}. Event subscriptions are managed by
 * the {@link EventWorker} from the {@link AwaitCondition}s a step declares, so
 * a step never subscribes or unsubscribes directly.
 */
public interface EventStepContext {

    FlowId flowId();

    ExecutionContext executionContext();

    /** Flow-level id of the step currently running. */
    String currentStepId();

    /**
     * Shared event bus, for publishing outbound events from a step (for
     * example, dispatching an LLM/tool request). Inbound waits are declared
     * with {@link AwaitCondition}, not by subscribing here.
     */
    EventBus eventBus();

    /**
     * Submit blocking or long-running work to the worker's offload executor.
     *
     * <p>The event-loop thread must stay non-blocking. LLM, MCP, HTTP,
     * database, tool, sleep, or long CPU work should be started through this
     * method and should publish only completion/failure events back through
     * {@link #eventBus()}.
     *
     * <p>If the {@link EventWorker} was not constructed with an offload
     * executor, this method fails fast.
     */
    void offload(Runnable task);

    Clock clock();

    /** Convenience for {@code clock().currentTimeMillis()}. */
    long now();
}
