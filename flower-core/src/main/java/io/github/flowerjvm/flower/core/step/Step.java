package io.github.flowerjvm.flower.core.step;

/**
 * Base class users extend to define an orchestration unit.
 *
 * <p>A Step is stateful and tied to one Flow instance. The framework calls
 * the lifecycle hooks below in order; the user implements
 * {@link #onTick(StepContext)} and overrides the others as needed.
 *
 * <pre>
 *   onEnter(ctx)            // called once when the Step becomes current
 *   onTick(ctx) repeatedly  // called every Worker tick until it returns
 *                           // DONE / GOTO / FINISH / FAIL / REPEAT
 *   onExit(ctx)             // called once when the Step is leaving (done/goto/finish/fail)
 *   onReset(ctx)            // called when the Step is being reset (repeat)
 *                           // followed by onEnter on the next tick
 * </pre>
 *
 * <p>Steps are <em>not</em> instantiated by reflection. Users construct them
 * directly inside a {@code FlowFactory} and pass dependencies through the
 * constructor.
 *
 * <p>Step is meant to be a thin orchestration class. Heavy domain logic,
 * DB access, protocol encoding, and external API calls belong in services
 * the Step calls into - not inside the Step itself.
 */
public abstract class Step {

    /**
     * Called once when the Step becomes the current Step of its Flow.
     * Subscriptions made here are auto-unsubscribed on exit/reset.
     */
    protected void onEnter(StepContext ctx) {
        // default: no-op
    }

    /**
     * Called on every Worker tick while this Step is current.
     * Returns the {@link StepResult} that drives the Flow's next move.
     */
    protected abstract StepResult onTick(StepContext ctx);

    /**
     * Called once when the Step is leaving (done, goto, finish, fail).
     * Not called when the Step is being reset for {@link StepResult#repeat()}.
     */
    protected void onExit(StepContext ctx) {
        // default: no-op
    }

    /**
     * Called when the Step is reset (e.g. {@link StepResult#repeat()}).
     * Use this to clear instance fields. The framework already clears
     * subscriptions, signals, timeout, and stepNo before this hook runs.
     */
    protected void onReset(StepContext ctx) {
        // default: no-op
    }
}
