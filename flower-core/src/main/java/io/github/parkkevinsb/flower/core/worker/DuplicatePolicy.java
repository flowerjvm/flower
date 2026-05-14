package io.github.parkkevinsb.flower.core.worker;

/**
 * Policy applied when a Flow with the same {@link io.github.parkkevinsb.flower.core.flow.FlowId}
 * is submitted to a Worker that already runs it.
 */
public enum DuplicatePolicy {
    /**
     * Reject the new submission with an exception. Default policy.
     */
    REJECT,

    /**
     * Cancel the existing flow and replace it with the new one.
     */
    REPLACE,

    /**
     * Silently ignore the new submission and keep the existing flow running.
     */
    IGNORE
}
