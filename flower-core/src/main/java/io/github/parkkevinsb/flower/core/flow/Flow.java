package io.github.parkkevinsb.flower.core.flow;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.step.GuardResult;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepDefinition;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.step.StepRuntime;
import io.github.parkkevinsb.flower.core.time.Clock;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A single ordered sequence of {@link Step}s tied to one domain instance.
 *
 * <p>A Flow is built via {@link #builder(String, String)}, then submitted
 * to a {@code Worker} which {@link #attach(Clock, EventBus)} the runtime
 * dependencies and drives {@link #tick()} on its tick loop.
 *
 * <p>Lifecycle (see {@link FlowState}):
 *
 * <pre>
 * CREATED  -- after build()
 * READY    -- after attach(clock, bus) inside Worker.submit()
 * RUNNING  -- after the first tick that enters a Step
 * FINISHED / FAILED / CANCELLED -- terminal
 * </pre>
 *
 * <p>Not thread-safe: a Flow is owned by one Worker and ticked from one
 * thread. Cross-thread interaction with a Flow happens only through
 * Step subscriptions and signals, which are handled by {@link StepRuntime}.
 */
public final class Flow {

    private final FlowId flowId;
    private final List<StepDefinition> steps;
    private final Map<String, Integer> stepIndexById;

    private FlowState state = FlowState.CREATED;
    private int currentIndex = -1;
    private StepRuntime currentRuntime;
    private boolean currentEntered;
    private Throwable failureCause;

    private Clock clock;
    private EventBus eventBus;
    private LifecycleObserver observer = LifecycleObserver.NOOP;

    Flow(FlowId flowId, List<StepDefinition> steps) {
        this.flowId = flowId;
        this.steps = Collections.unmodifiableList(steps);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            idx.put(steps.get(i).stepId(), i);
        }
        this.stepIndexById = Collections.unmodifiableMap(idx);
    }

    public static FlowBuilder builder(String flowType, String flowKey) {
        return new FlowBuilder(flowType, flowKey);
    }

    public FlowId flowId() {
        return flowId;
    }

    public FlowState state() {
        return state;
    }

    public Throwable failureCause() {
        return failureCause;
    }

    public List<StepDefinition> steps() {
        return steps;
    }

    public String currentStepId() {
        if (state.isTerminal()) {
            return null;
        }
        if (currentIndex < 0 || currentIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentIndex).stepId();
    }

    public int currentStepNo() {
        return currentRuntime == null ? 0 : currentRuntime.stepNo();
    }

    public FlowSnapshot snapshot() {
        return new FlowSnapshot(flowId, state, currentStepId(), currentStepNo(), failureCause);
    }

    // ------------------------------------------------------------------
    // Worker-facing lifecycle
    // ------------------------------------------------------------------

    /**
     * Bind runtime dependencies. Called by the Worker on submit.
     */
    public void attach(Clock clock, EventBus eventBus) {
        attach(clock, eventBus, LifecycleObserver.NOOP);
    }

    /**
     * Bind runtime dependencies with a step transition observer. Used by
     * Worker so it can forward step-entered/exited events to FlowerListeners.
     */
    public void attach(Clock clock, EventBus eventBus, LifecycleObserver observer) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (observer == null) {
            throw new IllegalArgumentException("observer must not be null");
        }
        if (state != FlowState.CREATED) {
            throw new IllegalStateException("Flow already attached, state=" + state);
        }
        this.clock = clock;
        this.eventBus = eventBus;
        this.observer = observer;
        this.state = FlowState.READY;
    }

    /**
     * Run one tick. The Worker calls this on every tick while the Flow is
     * non-terminal. A tick performs at most one {@link Step#onTick(io.github.parkkevinsb.flower.core.step.StepContext)}
     * call so that long Flows do not starve their peers.
     */
    public void tick() {
        if (state.isTerminal()) {
            return;
        }
        if (state == FlowState.READY) {
            if (steps.isEmpty()) {
                state = FlowState.FINISHED;
                return;
            }
            state = FlowState.RUNNING;
            currentIndex = 0;
        }

        StepDefinition def = steps.get(currentIndex);
        ensureCurrentRuntime(def);
        if (!checkGuard(def)) {
            return;
        }

        // Fresh entry or post-repeat re-entry: enter the current step first.
        if (!currentEntered) {
            enterCurrent();
            if (state.isTerminal() || currentRuntime == null) {
                return;
            }
        }

        StepResult result;
        try {
            result = currentRuntime.tick(def.step());
            if (result == null) {
                result = StepResult.fail(new IllegalStateException(
                        "Step.onTick returned null for stepId=" + def.stepId()));
            }
        } catch (Throwable t) {
            result = StepResult.fail(t);
        }
        applyResult(result);
    }

    /**
     * Cancel the Flow. If a Step is current, its {@code onExit} runs and
     * subscriptions are released. Subsequent ticks are no-ops.
     */
    public void cancel() {
        if (state.isTerminal()) {
            return;
        }
        if (currentRuntime != null) {
            StepDefinition def = steps.get(currentIndex);
            boolean notifyExit = currentEntered;
            try {
                if (currentEntered) {
                    currentRuntime.exit(def.step());
                } else {
                    currentRuntime.dispose();
                }
            } catch (Throwable ignored) {
                // best-effort cleanup
            } finally {
                currentRuntime = null;
                currentEntered = false;
            }
            if (notifyExit) {
                notifyExited(def.stepId());
            }
        }
        state = FlowState.CANCELLED;
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void ensureCurrentRuntime(StepDefinition def) {
        if (currentRuntime == null) {
            currentRuntime = new StepRuntime(flowId, def.stepId(), clock, eventBus);
            currentEntered = false;
        }
    }

    private boolean checkGuard(StepDefinition def) {
        if (def.guard() == null) {
            return true;
        }
        GuardResult result;
        try {
            result = def.guard().check(currentRuntime);
            if (result == null) {
                result = GuardResult.fail(new IllegalStateException(
                        "Guard returned null for stepId=" + def.stepId()));
            }
        } catch (Throwable t) {
            result = GuardResult.fail(t);
        }

        switch (result.type()) {
            case PASS:
                return true;
            case HOLD:
                return false;
            case GOTO:
                applyGuardGoTo(def, result.targetStepId());
                return false;
            case FAIL:
                failureCause = result.cause();
                state = FlowState.FAILED;
                exitCurrent(def);
                return false;
            default:
                failureCause = new IllegalStateException(
                        "Unknown GuardResult type: " + result.type());
                state = FlowState.FAILED;
                exitCurrent(def);
                return false;
        }
    }

    private void applyGuardGoTo(StepDefinition def, String targetStepId) {
        Integer target = stepIndexById.get(targetStepId);
        if (target == null) {
            failureCause = new IllegalStateException(
                    "guard goTo target stepId not found: " + targetStepId);
            state = FlowState.FAILED;
            exitCurrent(def);
            return;
        }
        exitCurrent(def);
        if (state.isTerminal()) return;
        currentIndex = target;
    }

    private void enterCurrent() {
        StepDefinition def = steps.get(currentIndex);
        ensureCurrentRuntime(def);
        try {
            currentRuntime.enter(def.step());
        } catch (Throwable t) {
            // onEnter blew up: dispose the partial runtime, mark Flow as failed.
            try {
                currentRuntime.dispose();
            } catch (Throwable ignored) {
                // ignore
            }
            currentRuntime = null;
            currentEntered = false;
            state = FlowState.FAILED;
            failureCause = t;
            return;
        }
        currentEntered = true;
        notifyEntered(def.stepId());
    }

    private void applyResult(StepResult result) {
        StepDefinition def = steps.get(currentIndex);
        switch (result.type()) {
            case STAY:
                return;

            case ADVANCE:
                exitCurrent(def);
                if (state.isTerminal()) return;
                if (currentIndex + 1 >= steps.size()) {
                    state = FlowState.FINISHED;
                    return;
                }
                currentIndex++;
                return;

            case REPEAT:
                try {
                    currentRuntime.reset(def.step());
                } catch (Throwable t) {
                    state = FlowState.FAILED;
                    failureCause = t;
                }
                // Discard the runtime; next tick re-creates it and calls onEnter again.
                currentRuntime = null;
                currentEntered = false;
                return;

            case GOTO: {
                Integer target = stepIndexById.get(result.targetStepId());
                if (target == null) {
                    Throwable cause = new IllegalStateException(
                            "goTo target stepId not found: " + result.targetStepId());
                    failureCause = cause;
                    state = FlowState.FAILED;
                    exitCurrent(def);
                    return;
                }
                exitCurrent(def);
                if (state.isTerminal()) return;
                currentIndex = target;
                return;
            }

            case DONE:
                exitCurrent(def);
                if (state.isTerminal()) return;
                state = FlowState.FINISHED;
                return;

            case FAIL:
                failureCause = result.cause();
                state = FlowState.FAILED;
                exitCurrent(def);
                return;

            default:
                // unreachable
                state = FlowState.FAILED;
                failureCause = new IllegalStateException(
                        "Unknown StepResult type: " + result.type());
        }
    }

    private void exitCurrent(StepDefinition def) {
        if (currentRuntime == null) return;
        try {
            if (currentEntered) {
                currentRuntime.exit(def.step());
            } else {
                currentRuntime.dispose();
            }
        } catch (Throwable t) {
            // If onExit fails and we are not already failing, capture it.
            if (state != FlowState.FAILED) {
                state = FlowState.FAILED;
                failureCause = t;
            }
        } finally {
            currentRuntime = null;
            boolean notifyExit = currentEntered;
            currentEntered = false;
            if (notifyExit) {
                notifyExited(def.stepId());
            }
        }
    }

    private void notifyEntered(String stepId) {
        try {
            observer.onStepEntered(stepId);
        } catch (Throwable ignored) {
            // observer must not derail the tick
        }
    }

    private void notifyExited(String stepId) {
        try {
            observer.onStepExited(stepId);
        } catch (Throwable ignored) {
            // observer must not derail the tick
        }
    }
}
