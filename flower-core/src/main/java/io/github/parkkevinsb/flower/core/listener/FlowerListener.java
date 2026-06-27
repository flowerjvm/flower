package io.github.parkkevinsb.flower.core.listener;

import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;

/**
 * Observer hook for Flow lifecycle events.
 *
 * <p>Listeners are invoked from the Worker tick thread. They must return
 * quickly and must not throw - implementations should swallow their own
 * exceptions. The framework guards against listener exceptions but does
 * not retry.
 *
 * <p>All methods have a no-op default so implementations can override
 * only the events they care about.
 */
public interface FlowerListener {

    /**
     * Fires when a Flow has been accepted by a Worker (transitioned to READY).
     */
    default void onFlowSubmitted(FlowSnapshot flow) {
    }

    /**
     * Fires when a Step becomes the current Step of its Flow.
     */
    default void onStepEntered(FlowSnapshot flow, String stepId) {
    }

    /**
     * Fires when a Step is leaving (done, goto, finish, fail, cancel).
     */
    default void onStepExited(FlowSnapshot flow, String stepId) {
    }

    /**
     * Fires when a Flow terminates successfully.
     */
    default void onFlowFinished(FlowSnapshot flow) {
    }

    /**
     * Fires when a Flow terminates with a Throwable, including durable
     * checkpoint persistence failures.
     */
    default void onFlowFailed(FlowSnapshot flow, Throwable cause) {
    }

    /**
     * Fires when a Flow terminates due to {@code cancel()}.
     */
    default void onFlowCancelled(FlowSnapshot flow) {
    }

    /**
     * Fires when another {@code FlowerListener} callback throws.
     *
     * <p>{@code callbackName} is the lifecycle callback that failed, such as
     * {@code onFlowFinished}. Exceptions thrown from this method are ignored.
     */
    default void onListenerError(FlowSnapshot flow, String callbackName, Throwable cause) {
    }

    /**
     * Fires when the Worker catches an unexpected framework-level error.
     *
     * <p>Step failures are reported through {@link #onFlowFailed(FlowSnapshot, Throwable)}.
     * This hook is for errors that escaped the normal Flow runtime boundary.
     * Exceptions thrown from this method are ignored.
     */
    default void onWorkerError(String workerName, Throwable cause) {
    }
}
