package io.github.parkkevinsb.flower.core.step;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.event.EventHandler;
import io.github.parkkevinsb.flower.core.event.Subscription;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.time.Clock;

/**
 * Orchestration helper passed into every {@link Step} lifecycle method.
 *
 * <p>{@code StepContext} is the only Flower API a Step is allowed to touch.
 * It exposes the bare minimum that an orchestration unit needs:
 *
 * <ul>
 *   <li>identity: which Flow and which Flow-level Step id is running</li>
 *   <li>internal cursor: {@link #stepNo()} and {@link #setStepNo(int)}</li>
 *   <li>event subscription with framework-managed lifetime</li>
 *   <li>step-local signals</li>
 *   <li>monotonic timeout helper backed by a {@link Clock}</li>
 * </ul>
 *
 * <p>Subscriptions registered through {@link #subscribe(Class, EventHandler)}
 * are unsubscribed automatically when the Step exits or is reset.
 *
 * <p>Signals can be set from any thread (typically an event handler thread).
 * The Worker tick reads them when it calls {@link Step#onTick(StepContext)}.
 */
public interface StepContext {

    FlowId flowId();

    /**
     * Flow-level Step id this context belongs to.
     * Use {@link io.github.parkkevinsb.flower.core.step.StepResult#goTo(String)}
     * with another Flow-level id to jump.
     */
    String currentStepId();

    int stepNo();

    void setStepNo(int stepNo);

    /**
     * Subscribe to events of the given type. The subscription is removed when
     * the Step exits, is reset, or its Flow terminates.
     */
    <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler);

    /**
     * Direct access to the {@link EventBus} for cases where the user needs to
     * publish an event from inside a Step. Subscriptions made through this
     * reference are NOT framework-managed.
     */
    EventBus eventBus();

    void signal(String name);

    boolean hasSignal(String name);

    void clearSignal(String name);

    /**
     * Start a timeout window relative to the current clock time. Replaces any
     * pending timeout on the same Step.
     */
    void startTimeout(long millis);

    boolean timedOut();

    /**
     * Milliseconds elapsed since the last {@link #startTimeout(long)} call.
     * Returns {@code 0} if no timeout was started.
     */
    long elapsedMillis();

    Clock clock();
}
