package io.github.flowerjvm.flower.core.step;

/**
 * Runtime state of a Step inside its current Flow execution.
 *
 * <p>Step internal progress (the {@code stepNo} cursor or named internal
 * step id) is tracked separately. This enum only describes the lifecycle
 * boundary observed by the Worker.
 */
public enum StepRuntimeState {
    IDLE,
    ENTERED,
    RUNNING,
    EXITED,
    FAILED
}
