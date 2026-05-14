package io.github.parkkevinsb.flower.core.flow;

import io.github.parkkevinsb.flower.core.step.Guard;
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

    FlowBuilder(String flowType, String flowKey) {
        this.flowType = flowType;
        this.flowKey = flowKey;
    }

    public FlowBuilder step(String stepId, Step step) {
        return step(stepId, step, null);
    }

    public FlowBuilder step(String stepId, Step step, Guard guard) {
        StepDefinition def = new StepDefinition(stepId, step, guard);
        if (!stepIds.add(def.stepId())) {
            throw new IllegalArgumentException("duplicate stepId in flow: " + stepId);
        }
        steps.add(def);
        return this;
    }

    public Flow build() {
        if (steps.isEmpty()) {
            throw new IllegalStateException("Flow must declare at least one step");
        }
        return new Flow(new FlowId(flowType, flowKey), steps);
    }
}
