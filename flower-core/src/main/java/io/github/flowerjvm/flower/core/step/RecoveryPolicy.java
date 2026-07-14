package io.github.flowerjvm.flower.core.step;

/**
 * Declares how a durable Step may be activated after checkpoint recovery.
 */
public enum RecoveryPolicy {
    /**
     * Recovery may call {@code onEnter} again before the Step is ticked.
     * Use this only when entering the Step is idempotent.
     */
    REENTER_IDEMPOTENT,

    /**
     * Recovery calls {@link DurableStep#onResume(StepContext)} instead of
     * {@code onEnter}. Use this when initial side effects and recovery setup
     * must be separated.
     */
    RESUME_ONLY
}
