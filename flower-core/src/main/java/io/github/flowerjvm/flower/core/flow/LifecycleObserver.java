package io.github.flowerjvm.flower.core.flow;

import io.github.flowerjvm.flower.core.trace.StepTransition;

/**
 * Internal hook used by a {@code Worker} to observe step transitions that
 * happen inside a single {@link Flow#tick()} call.
 *
 * <p>This is needed because a tick can both enter and exit a step (for
 * example when the step returns {@code DONE} on its first tick), and the
 * Worker cannot otherwise observe the intermediate state.
 *
 * <p>Not part of the public user-facing API. Users observe lifecycle through
 * {@link io.github.flowerjvm.flower.core.listener.FlowerListener} instead.
 */
public interface LifecycleObserver {

    /**
     * No-op observer used when a Flow is not attached to a Worker.
     */
    LifecycleObserver NOOP = new LifecycleObserver() {
        @Override public void onStepEntered(String stepId) {}
        @Override public void onStepExited(String stepId) {}
    };

    /** Fires immediately before a fresh or recovered Step lifecycle starts. */
    default void onStepStarted(String stepId, int stepNo, boolean recovered) {
    }

    void onStepEntered(String stepId);

    void onStepExited(String stepId);

    /** Fires after a Step, Guard, lifecycle failure, or cancellation selects a transition. */
    default void onStepTransitioned(StepTransition transition) {
    }
}
