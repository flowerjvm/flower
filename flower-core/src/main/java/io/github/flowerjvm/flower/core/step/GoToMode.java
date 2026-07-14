package io.github.flowerjvm.flower.core.step;

/**
 * How a Flow should treat the current Step when {@link StepResult#goTo(String)}
 * is returned.
 *
 * <p>The current runtime supports {@link #COMPLETE_CURRENT}. Additional jump
 * semantics can be added later if an application needs a different lifecycle
 * behavior.
 */
public enum GoToMode {
    /**
     * Mark the current Step as exited and jump to the target Step.
     */
    COMPLETE_CURRENT
}
