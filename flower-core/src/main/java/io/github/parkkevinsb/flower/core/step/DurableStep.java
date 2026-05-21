package io.github.parkkevinsb.flower.core.step;

/**
 * Base class for Steps that can be used inside a durable Flow.
 *
 * <p>Durability is explicit on purpose: a regular {@link Step} may still be
 * used in a durable Flow when the builder is given a {@link RecoveryPolicy},
 * but extending this class lets the Step carry that recovery contract itself.
 */
public abstract class DurableStep extends Step {

    private final RecoveryPolicy recoveryPolicy;

    protected DurableStep() {
        this(RecoveryPolicy.REENTER_IDEMPOTENT);
    }

    protected DurableStep(RecoveryPolicy recoveryPolicy) {
        if (recoveryPolicy == null) {
            throw new IllegalArgumentException("recoveryPolicy must not be null");
        }
        this.recoveryPolicy = recoveryPolicy;
    }

    public final RecoveryPolicy recoveryPolicy() {
        return recoveryPolicy;
    }

    /**
     * Called instead of {@code onEnter} when this Step is recovered with
     * {@link RecoveryPolicy#RESUME_ONLY}.
     */
    protected void onResume(StepContext ctx) {
        throw new IllegalStateException(
                "DurableStep with RESUME_ONLY must override onResume: " + getClass().getName());
    }
}
