package io.github.parkkevinsb.flower.core.flow;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpoint;
import io.github.parkkevinsb.flower.core.step.GuardResult;
import io.github.parkkevinsb.flower.core.step.RecoveryPolicy;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepDefinition;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.step.StepRuntime;
import io.github.parkkevinsb.flower.core.time.Clock;

import java.util.ArrayList;
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
    private final FlowPersistence persistence;
    private final String definitionVersion;
    private ExecutionContext executionContext;

    private FlowState state = FlowState.CREATED;
    private int currentIndex = -1;
    private StepRuntime currentRuntime;
    private boolean currentEntered;
    private boolean recoveringCurrentStep;
    private int retainedStepNo;
    private Throwable failureCause;
    private FlowCheckpoint recoveryCheckpoint;

    private Clock clock;
    private EventBus eventBus;
    private LifecycleObserver observer = LifecycleObserver.NOOP;

    Flow(
            FlowId flowId,
            List<StepDefinition> steps,
            FlowPersistence persistence,
            String definitionVersion,
            ExecutionContext executionContext) {
        this.flowId = flowId;
        this.steps = Collections.unmodifiableList(steps);
        this.persistence = persistence == null ? FlowPersistence.TRANSIENT : persistence;
        this.definitionVersion = definitionVersion;
        this.executionContext = executionContext == null ? ExecutionContext.empty() : executionContext;
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

    public FlowPersistence persistence() {
        return persistence;
    }

    public String definitionVersion() {
        return definitionVersion;
    }

    public ExecutionContext executionContext() {
        return executionContext;
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

    public int currentStepIndex() {
        if (state.isTerminal()) {
            return -1;
        }
        if (currentIndex < 0 || currentIndex >= steps.size()) {
            return -1;
        }
        return currentIndex;
    }

    public int currentStepNo() {
        if (currentRuntime != null) {
            return currentRuntime.stepNo();
        }
        return recoveringCurrentStep ? retainedStepNo : 0;
    }

    public FlowSnapshot snapshot() {
        return snapshot(false);
    }

    public FlowSnapshot snapshotWithStepDefinitions() {
        return snapshot(true);
    }

    private FlowSnapshot snapshot(boolean includeStepDefinitions) {
        return new FlowSnapshot(
                flowId,
                state,
                currentStepId(),
                currentStepIndex(),
                currentStepNo(),
                includeStepDefinitions
                        ? stepSnapshots()
                        : Collections.<FlowStepSnapshot>emptyList(),
                failureCause,
                executionContext);
    }

    public FlowCheckpoint checkpoint(String workerName, long updatedAtMillis) {
        return new FlowCheckpoint(
                flowId,
                state,
                currentStepId(),
                currentStepNo(),
                currentEntered || recoveringCurrentStep,
                persistence,
                workerName,
                updatedAtMillis,
                definitionVersion,
                executionContext);
    }

    private List<FlowStepSnapshot> stepSnapshots() {
        List<FlowStepSnapshot> out = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            out.add(FlowStepSnapshot.from(i, steps.get(i)));
        }
        return out;
    }

    /**
     * Prepare this freshly-built durable Flow to resume from a saved checkpoint.
     *
     * <p>The Flow must still be in {@link FlowState#CREATED}; runtime services
     * are bound later by {@link #attach(Clock, EventBus, LifecycleObserver)}.
     */
    public Flow recoverFrom(FlowCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint must not be null");
        }
        if (state != FlowState.CREATED) {
            throw new IllegalStateException("Flow can only recover before attach, state=" + state);
        }
        if (persistence != FlowPersistence.DURABLE) {
            throw new IllegalStateException("Only durable flows can recover from checkpoints: " + flowId);
        }
        if (checkpoint.persistence() != FlowPersistence.DURABLE) {
            throw new IllegalArgumentException("checkpoint is not durable: " + checkpoint);
        }
        if (!flowId.equals(checkpoint.flowId())) {
            throw new IllegalArgumentException(
                    "checkpoint flowId mismatch. expected=" + flowId + ", actual=" + checkpoint.flowId());
        }
        if (checkpoint.state().isTerminal()) {
            throw new IllegalArgumentException("terminal checkpoints cannot be recovered: " + checkpoint);
        }
        if (checkpoint.state() != FlowState.READY && checkpoint.state() != FlowState.RUNNING) {
            throw new IllegalArgumentException("checkpoint must be READY or RUNNING: " + checkpoint);
        }
        if (definitionVersion != null
                && checkpoint.definitionVersion() != null
                && !definitionVersion.equals(checkpoint.definitionVersion())) {
            throw new IllegalArgumentException(
                    "checkpoint definitionVersion mismatch. expected=" + definitionVersion
                            + ", actual=" + checkpoint.definitionVersion());
        }
        if (checkpoint.state() == FlowState.RUNNING && !stepIndexById.containsKey(checkpoint.currentStepId())) {
            throw new IllegalArgumentException(
                    "checkpoint stepId not found in flow: " + checkpoint.currentStepId());
        }
        if (!checkpoint.executionContext().isEmpty()) {
            this.executionContext = checkpoint.executionContext();
        }
        this.recoveryCheckpoint = checkpoint;
        return this;
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
        if (recoveryCheckpoint != null) {
            applyRecoveryCheckpoint(recoveryCheckpoint);
        }
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

        // Fresh entry, post-repeat re-entry, or checkpoint recovery activation.
        if (!currentEntered) {
            if (recoveringCurrentStep) {
                resumeCurrent();
            } else {
                enterCurrent();
            }
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
                recoveringCurrentStep = false;
                retainedStepNo = 0;
            }
            if (notifyExit) {
                notifyExited(def.stepId());
            }
        }
        state = FlowState.CANCELLED;
    }

    /**
     * Release runtime resources without marking the Flow terminal. Used when a
     * Worker stops while preserving durable checkpoints for later recovery.
     */
    public void suspend() {
        if (state.isTerminal()) {
            return;
        }
        if (currentRuntime != null) {
            int stepNo = currentRuntime.stepNo();
            try {
                currentRuntime.dispose();
            } catch (Throwable ignored) {
                // best-effort cleanup
            } finally {
                currentRuntime = null;
                if (currentEntered) {
                    retainedStepNo = stepNo;
                    recoveringCurrentStep = true;
                }
                currentEntered = false;
            }
        }
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void ensureCurrentRuntime(StepDefinition def) {
        if (currentRuntime == null) {
            currentRuntime = new StepRuntime(flowId, executionContext, def.stepId(), clock, eventBus);
            currentEntered = false;
        }
    }

    private void applyRecoveryCheckpoint(FlowCheckpoint checkpoint) {
        if (checkpoint.state() == FlowState.READY) {
            state = FlowState.READY;
            currentIndex = -1;
            currentRuntime = null;
            currentEntered = false;
            recoveringCurrentStep = false;
            retainedStepNo = 0;
            return;
        }

        Integer index = stepIndexById.get(checkpoint.currentStepId());
        if (index == null) {
            throw new IllegalStateException(
                    "checkpoint stepId not found in flow: " + checkpoint.currentStepId());
        }
        state = FlowState.RUNNING;
        currentIndex = index;
        currentEntered = false;
        recoveringCurrentStep = checkpoint.currentStepEntered();
        currentRuntime = new StepRuntime(flowId, executionContext, checkpoint.currentStepId(), clock, eventBus);
        currentRuntime.setStepNo(checkpoint.currentStepNo());
        retainedStepNo = 0;
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
            recoveringCurrentStep = false;
            retainedStepNo = 0;
            state = FlowState.FAILED;
            failureCause = t;
            return;
        }
        currentEntered = true;
        recoveringCurrentStep = false;
        retainedStepNo = 0;
        notifyEntered(def.stepId());
    }

    private void resumeCurrent() {
        StepDefinition def = steps.get(currentIndex);
        ensureCurrentRuntime(def);
        RecoveryPolicy recoveryPolicy = def.recoveryPolicy();
        try {
            currentRuntime.resume(def.step(), recoveryPolicy);
        } catch (Throwable t) {
            try {
                currentRuntime.dispose();
            } catch (Throwable ignored) {
                // ignore
            }
            currentRuntime = null;
            currentEntered = false;
            recoveringCurrentStep = false;
            retainedStepNo = 0;
            state = FlowState.FAILED;
            failureCause = t;
            return;
        }
        currentEntered = true;
        recoveringCurrentStep = false;
        retainedStepNo = 0;
        notifyEntered(def.stepId());
    }

    private void applyResult(StepResult result) {
        StepDefinition def = steps.get(currentIndex);
        switch (result.type()) {
            case STAY:
                return;

            case DONE:
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
                recoveringCurrentStep = false;
                retainedStepNo = 0;
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

            case FINISH:
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
            recoveringCurrentStep = false;
            retainedStepNo = 0;
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
