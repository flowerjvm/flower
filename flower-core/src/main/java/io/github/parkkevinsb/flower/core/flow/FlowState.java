package io.github.parkkevinsb.flower.core.flow;

/**
 * Lifecycle state of a {@link Flow}.
 *
 * <pre>
 * CREATED  -- builder produced, not yet submitted
 * READY    -- accepted by a Worker, waiting for first tick
 * RUNNING  -- ticking inside a Worker
 * FINISHED -- terminated successfully
 * FAILED   -- terminated with a Throwable
 * CANCELLED-- terminated by external cancel
 * </pre>
 */
public enum FlowState {
    CREATED,
    READY,
    RUNNING,
    FINISHED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == FINISHED || this == FAILED || this == CANCELLED;
    }
}
