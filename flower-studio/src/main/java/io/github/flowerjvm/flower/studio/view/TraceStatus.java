package io.github.flowerjvm.flower.studio.view;

/** Projected local Studio run state. */
public enum TraceStatus {
    COMPLETED,
    RUNNING,
    WAITING,
    INTERRUPTED,
    CANCELLED,
    FAILED,
    UNKNOWN
}
