package io.github.flowerjvm.flower.core.step;

/**
 * How a Flow should treat the current Step when {@link StepResult#goTo(String)}
 * is returned.
 *
 * <p>The runtime interprets this value when applying a {@code GOTO} result.
 * The current runtime supports {@link #COMPLETE_CURRENT}. Additional jump
 * semantics require an explicit lifecycle implementation before a new value
 * is added.
 */
public enum GoToMode {
    /**
     * Mark the current Step as exited and jump to the target Step.
     */
    COMPLETE_CURRENT
}
