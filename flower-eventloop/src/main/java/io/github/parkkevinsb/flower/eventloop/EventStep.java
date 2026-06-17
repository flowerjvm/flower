package io.github.parkkevinsb.flower.eventloop;

/**
 * A single orchestration unit in the event-driven runtime.
 *
 * <p>Lifecycle:
 *
 * <pre>
 * onEnter(ctx)            called once when the step becomes current.
 *                         Return await(...) to wait, or a transition result.
 * onEvent(ctx, event)     called when an awaited event is delivered.
 *                         Return null to ignore and keep waiting.
 * onTimeout(ctx)          called when an awaited deadline is reached.
 *                         Default fails the flow with a timeout.
 * onRecover(ctx, recovery)
 *                         called when a durable step is restored from a
 *                         checkpoint. Re-register awaits; do not repeat
 *                         one-shot request effects.
 * onExit(ctx)             called when the step leaves by next/goTo/finish/fail.
 * </pre>
 *
 * <p>No method blocks a thread waiting for outside work. Start the work in
 * {@code onEnter} (for example publish a request through
 * {@link EventStepContext#eventBus()}), declare what should wake the step, and
 * decide the transition when the wake-up arrives.
 *
 * <p>All callbacks run on the event-loop thread. They must be quick and
 * non-blocking. For LLM, MCP, HTTP, database, or tool work, use
 * {@link EventStepContext#offload(Runnable)} or another external service and
 * publish only the completion event back to the worker's
 * {@link EventStepContext#eventBus()}.
 */
public abstract class EventStep {

    /**
     * Called once when this step becomes current. Typically starts outbound
     * work and returns {@link EventStepResult#await(AwaitCondition...)}.
     */
    protected abstract EventStepResult onEnter(EventStepContext ctx);

    /**
     * Called when an event matching one of this step's await conditions is
     * delivered. The default ignores the event and keeps the current awaits.
     *
     * @return a transition result, or {@code null} to keep waiting
     */
    protected EventStepResult onEvent(EventStepContext ctx, Object event) {
        return null;
    }

    /**
     * Called when a declared deadline is reached. The default fails the flow.
     *
     * @return a transition result, or {@code null} to keep waiting on any
     *         remaining (non-deadline) conditions
     */
    protected EventStepResult onTimeout(EventStepContext ctx) {
        return EventStepResult.fail(
                new EventTimeoutException("step '" + ctx.currentStepId() + "' timed out"));
    }

    /**
     * Called when this step is restored from a durable event-flow checkpoint.
     *
     * <p>The default fails fast so one-shot effects such as LLM/tool request
     * publication are not accidentally repeated by delegating to
     * {@link #onEnter(EventStepContext)}.
     */
    protected EventStepResult onRecover(EventStepContext ctx, EventRecoveryContext recovery) {
        return EventStepResult.fail(new IllegalStateException(
                "step '" + ctx.currentStepId() + "' does not implement onRecover"));
    }

    /** Called when the step leaves. Default does nothing. */
    protected void onExit(EventStepContext ctx) {
    }
}
