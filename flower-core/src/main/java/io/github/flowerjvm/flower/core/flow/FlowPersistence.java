package io.github.flowerjvm.flower.core.flow;

/**
 * Persistence mode for a {@link Flow}.
 *
 * <p>The default is {@link #TRANSIENT}. Durable flows use Flower's checkpoint
 * contract so an application can rebuild a fresh Flow and resume it from the
 * last saved step position.
 */
public enum FlowPersistence {
    /**
     * In-memory only. This is the historical Flower behavior.
     */
    TRANSIENT,

    /**
     * Checkpoint/resume capable. This does not make Step side effects or event
     * payloads durable; it only marks the Flow as eligible for position
     * checkpoints.
     */
    DURABLE
}
