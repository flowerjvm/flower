package io.github.flowerjvm.flower.core.worker;

/**
 * Lifecycle state of a {@link Worker}.
 */
public enum WorkerState {
    CREATED,
    ATTACHED,
    RUNNING,
    PAUSED,
    STOPPING,
    STOPPED;

    public boolean isTerminal() {
        return this == STOPPED;
    }
}
