package io.github.flowerjvm.flower.core.trace;

/**
 * Stable runtime event types emitted by Flower core.
 *
 * <p>Domain modules such as Agent, AI Harness, and Action Runtime keep their
 * own event vocabularies. These types describe only Flow execution facts that
 * Flower itself owns.
 */
public enum FlowerTraceEventType {
    FLOW_STARTED,
    FLOW_RECOVERED,
    STEP_STARTED,
    STEP_SKIPPED,
    STEP_COMPLETED,
    STEP_FAILED,
    STEP_CANCELLED,
    FLOW_WAITING,
    FLOW_RESUMED,
    CHECKPOINT_SAVED,
    CHECKPOINT_FAILED,
    FLOW_SUSPENDED,
    FLOW_COMPLETED,
    FLOW_FAILED,
    FLOW_CANCELLED
}
