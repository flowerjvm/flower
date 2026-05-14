package io.github.parkkevinsb.flower.core.step;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.event.EventHandler;
import io.github.parkkevinsb.flower.core.event.Subscription;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.time.Clock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Framework-internal {@link StepContext} implementation.
 *
 * <p>One {@code StepRuntime} backs one Step entry. The lifecycle methods
 * ({@link #enter(Step)}, {@link #tick(Step)}, {@link #exit(Step)},
 * {@link #reset(Step)}) are public for the Worker/Flow runtime in sibling
 * packages to call. They are <em>not</em> part of the user-facing API.
 *
 * <p>Mutable state ownership:
 * <ul>
 *   <li>{@code stepNo}, {@code timeoutStart}, {@code timeoutMillis} - written and
 *       read only on the Worker thread</li>
 *   <li>{@code signals}, {@code subscriptions} - safe to mutate from event
 *       handler threads ({@link java.util.concurrent.ConcurrentHashMap} backed
 *       set, copy-on-iterate list)</li>
 * </ul>
 */
public final class StepRuntime implements StepContext {

    private static final long NO_TIMEOUT = -1L;

    private final FlowId flowId;
    private final String stepId;
    private final Clock clock;
    private final EventBus eventBus;

    private final Set<String> signals = ConcurrentHashMap.newKeySet();
    private final List<Subscription> subscriptions = Collections.synchronizedList(new ArrayList<>());

    private int stepNo;
    private long timeoutStart = NO_TIMEOUT;
    private long timeoutMillis = 0L;

    public StepRuntime(FlowId flowId, String stepId, Clock clock, EventBus eventBus) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        if (stepId == null || stepId.isEmpty()) {
            throw new IllegalArgumentException("stepId must not be null or empty");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        // eventBus may be null when a Flow is built without one - subscribe will fail loudly.
        this.flowId = flowId;
        this.stepId = stepId;
        this.clock = clock;
        this.eventBus = eventBus;
    }

    // ------------------------------------------------------------------
    // StepContext - user-facing API
    // ------------------------------------------------------------------

    @Override
    public FlowId flowId() {
        return flowId;
    }

    @Override
    public String currentStepId() {
        return stepId;
    }

    @Override
    public int stepNo() {
        return stepNo;
    }

    @Override
    public void setStepNo(int stepNo) {
        this.stepNo = stepNo;
    }

    @Override
    public <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler) {
        if (eventBus == null) {
            throw new IllegalStateException(
                    "Cannot subscribe: this Flow was built without an EventBus");
        }
        Subscription sub = eventBus.subscribe(eventType, handler);
        subscriptions.add(sub);
        return sub;
    }

    @Override
    public EventBus eventBus() {
        if (eventBus == null) {
            throw new IllegalStateException(
                    "No EventBus is bound to this Flow");
        }
        return eventBus;
    }

    @Override
    public void signal(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("signal name must not be null or empty");
        }
        signals.add(name);
    }

    @Override
    public boolean hasSignal(String name) {
        return name != null && signals.contains(name);
    }

    @Override
    public void clearSignal(String name) {
        if (name != null) {
            signals.remove(name);
        }
    }

    @Override
    public void startTimeout(long millis) {
        if (millis < 0L) {
            throw new IllegalArgumentException("timeout millis must not be negative: " + millis);
        }
        this.timeoutStart = clock.currentTimeMillis();
        this.timeoutMillis = millis;
    }

    @Override
    public boolean timedOut() {
        if (timeoutStart == NO_TIMEOUT) {
            return false;
        }
        return clock.currentTimeMillis() - timeoutStart >= timeoutMillis;
    }

    @Override
    public long elapsedMillis() {
        if (timeoutStart == NO_TIMEOUT) {
            return 0L;
        }
        return clock.currentTimeMillis() - timeoutStart;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    // ------------------------------------------------------------------
    // Framework lifecycle - called by Flow runtime in sibling packages
    // ------------------------------------------------------------------

    /**
     * Drive {@link Step#onEnter(StepContext)}. Same-package access lets us
     * reach the Step's {@code protected} hook.
     */
    public void enter(Step step) {
        step.onEnter(this);
    }

    /**
     * Drive {@link Step#onTick(StepContext)}.
     */
    public StepResult tick(Step step) {
        return step.onTick(this);
    }

    /**
     * Drive {@link Step#onExit(StepContext)}, then dispose subscriptions and
     * timer state. Signals are kept untouched - exit is final, the Step will
     * not be ticked again.
     */
    public void exit(Step step) {
        try {
            step.onExit(this);
        } finally {
            disposeSubscriptions();
            timeoutStart = NO_TIMEOUT;
            timeoutMillis = 0L;
        }
    }

    /**
     * Reset the runtime so the Step can be re-entered. Unsubscribes all
     * framework-managed subscriptions, clears signals and timer, resets
     * stepNo to 0, then drives {@link Step#onReset(StepContext)}.
     */
    public void reset(Step step) {
        disposeSubscriptions();
        signals.clear();
        timeoutStart = NO_TIMEOUT;
        timeoutMillis = 0L;
        stepNo = 0;
        step.onReset(this);
    }

    /**
     * Dispose any state that should not survive the Step. Used by Flow
     * teardown paths (cancel, fail, done) where {@link #exit(Step)} is
     * not appropriate.
     */
    public void dispose() {
        disposeSubscriptions();
        signals.clear();
        timeoutStart = NO_TIMEOUT;
        timeoutMillis = 0L;
    }

    public int subscriptionCount() {
        return subscriptions.size();
    }

    private void disposeSubscriptions() {
        // synchronizedList: iterate under the list's monitor and copy out, then
        // unsubscribe outside the lock to avoid holding it during user callbacks.
        Subscription[] copy;
        synchronized (subscriptions) {
            copy = subscriptions.toArray(new Subscription[0]);
            subscriptions.clear();
        }
        for (Subscription s : copy) {
            try {
                s.unsubscribe();
            } catch (Throwable ignored) {
                // best-effort cleanup
            }
        }
    }
}
