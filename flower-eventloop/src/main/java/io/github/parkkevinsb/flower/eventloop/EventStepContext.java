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

    Clock clock();

    /** Convenience for {@code clock().currentTimeMillis()}. */
    long now();
}
