package io.github.parkkevinsb.flower.core.flow;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.step.Guard;
import io.github.parkkevinsb.flower.core.step.RecoveryPolicy;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fluent builder for {@link Flow}.
 *
 * <p>Step ids must be unique within a Flow. The builder uses Flow-level
 * string ids - the same Step class can be added more than once under
 * different ids. There is no reflection or DI: the user constructs each
 * Step directly and passes its dependencies through the constructor.
 */
public final class FlowBuilder {

    private final String flowType;
    private final String flowKey;
    private final List<StepDefinition> steps = new ArrayList<>();
    private final Set<String> stepIds = new HashSet<>();
    private FlowPersistence persistence = FlowPersistence.TRANSIENT;
    private ExecutionContext executionContext = ExecutionContext.empty();
    private String definitionVersion;

    FlowBuilder(String flowType, String flowKey) {
        this.flowType = flowType;
        this.flowKey = flowKey;
    }

    public FlowBuilder step(String stepId, Step step) {
        return step(stepId, step, (Guard) null);
    }

    public FlowBuilder step(String stepId, Step step, Guard guard) {
        return step(stepId, step, guard, null);
    }

    public FlowBuilder durableStep(String stepId, Step step, RecoveryPolicy recoveryPolicy) {
        return step(stepId, step, null, recoveryPolicy);
    }

    public FlowBuilder durableStep(String stepId, Step step, Guard guard, RecoveryPolicy recoveryPolicy) {
        return step(stepId, step, guard, recoveryPolicy);
    }

    private FlowBuilder step(String stepId, Step step, Guard guard, RecoveryPolicy recoveryPolicy) {
        StepDefinition def = new StepDefinition(stepId, step, guard, recoveryPolicy);
        if (!stepIds.add(def.stepId())) {
            throw new IllegalArgumentException("duplicate stepId in flow: " + stepId);
        }
        steps.add(def);
        return this;
    }

    public FlowBuilder durable() {
        return persistence(FlowPersistence.DURABLE);
    }

    public FlowBuilder persistence(FlowPersistence persistence) {
        if (persistence == null) {
            throw new IllegalArgumentException("persistence must not be null");
        }
        this.persistence = persistence;
        return this;
    }

    public FlowBuilder executionContext(ExecutionContext executionContext) {
        if (executionContext == null) {
            throw new IllegalArgumentException("executionContext must not be null");
        }
        this.executionContext = executionContext;
        return this;
    }

    public FlowBuilder definitionVersion(String definitionVersion) {
        if (definitionVersion != null && definitionVersion.isEmpty()) {
            throw new IllegalArgumentException("definitionVersion must not be empty");
        }
        this.definitionVersion = definitionVersion;
        return this;
    }

    public Flow build() {
        if (steps.isEmpty()) {
            throw new IllegalStateException("Flow must declare at least one step");
        }
        validateDurableSteps();
        return new Flow(new FlowId(flowType, flowKey), steps, persistence, definitionVersion, executionContext);
    }

    private void validateDurableSteps() {
        if (persistence != FlowPersistence.DURABLE) {
            return;
        }
        for (StepDefinition def : steps) {
            if (!def.recoverable()) {
                throw new IllegalStateException(
                        "Durable flow requires a recovery policy for step: " + def.stepId());
            }
        }
    }
}
