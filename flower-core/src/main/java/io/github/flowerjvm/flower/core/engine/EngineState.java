package io.github.flowerjvm.flower.core.engine;

/**
 * Lifecycle state of an {@link Engine}.
 */
public enum EngineState {
    CREATED,
    RUNNING,
    STOPPING,
    STOPPED;

    public boolean isTerminal() {
        return this == STOPPED;
    }
}
