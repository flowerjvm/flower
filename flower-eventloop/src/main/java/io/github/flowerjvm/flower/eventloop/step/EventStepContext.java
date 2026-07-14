package io.github.flowerjvm.flower.eventloop.step;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.event.EventBus;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.time.Clock;
import io.github.flowerjvm.flower.eventloop.event.EventSignal;
import io.github.flowerjvm.flower.eventloop.worker.EventWorker;

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

    /** Publish a named external signal through the shared event bus. */
    default void signal(String name, String key) {
        eventBus().publish(EventSignal.of(name, key));
    }

    /** Publish a named external signal with a payload through the shared event bus. */
    default void signal(String name, String key, Object payload) {
        eventBus().publish(EventSignal.of(name, key, payload));
    }

    /**
     * Submit blocking or long-running work to the worker's async executor.
     *
     * <p>The event-loop thread must stay non-blocking. LLM, MCP, HTTP,
     * database, tool, sleep, or long CPU work should be started through this
     * method and should publish only completion/failure events back through
     * {@link #eventBus()}.
     *
     * <p>The submitted task runs outside the event-loop thread. Treat this
     * context as a publishing handle there: use {@link #eventBus()} or
     * {@link #signal(String, String, Object)} to report completion, and capture
     * immutable values such as ids before scheduling the task. Do not read
     * mutable flow state such as {@link #currentStepId()} from the async task.
     *
     * <p>If the {@link EventWorker} was not constructed with an async
     * executor, this method fails fast.
     */
    void runAsync(Runnable task);

    Clock clock();

    /** Convenience for {@code clock().currentTimeMillis()}. */
    long now();
}
