package io.github.parkkevinsb.flower.core.worker;

/**
 * Lifecycle state of a {@link Worker}.
 */
public enum WorkerState {
    CREATED,
    RUNNING,
    PAUSED,
    STOPPING,
    STOPPED;

    public boolean isTerminal() {
        return this == STOPPED;
    }
}
