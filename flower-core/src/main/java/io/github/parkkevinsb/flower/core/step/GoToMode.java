package io.github.parkkevinsb.flower.core.step;

/**
 * How a Flow should treat the current Step when {@link StepResult#goTo(String)}
 * is returned.
 *
 * <p>v1 ships only {@link #COMPLETE_CURRENT}. {@code REQUEUE_CURRENT} from
 * the legacy {@code REDIRECT_REQ} pattern is intentionally deferred until
 * a real use case appears.
 */
public enum GoToMode {
    /**
     * Mark the current Step as exited and jump to the target Step.
     */
    COMPLETE_CURRENT
}
