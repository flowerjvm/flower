package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.flow.FlowId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fluent builder for {@link EventFlow}. */
public final class EventFlowBuilder {

    private final FlowId flowId;
    private final List<EventStepDefinition> steps = new ArrayList<>();
    private final Set<String> stepIds = new HashSet<>();
    private ExecutionContext executionContext = ExecutionContext.empty();

    EventFlowBuilder(String flowType, String flowKey) {
        this.flowId = FlowId.of(flowType, flowKey);
    }

    public EventFlowBuilder executionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext == null ? ExecutionContext.empty() : executionContext;
        return this;
    }

    public EventFlowBuilder step(String stepId, EventStep step) {
        EventStepDefinition def = new EventStepDefinition(stepId, step);
        if (!stepIds.add(def.stepId())) {
            throw new IllegalArgumentException("duplicate stepId: " + stepId);
        }
        steps.add(def);
        return this;
    }

    public EventFlow build() {
        if (steps.isEmpty()) {
            throw new IllegalStateException("an EventFlow needs at least one step");
        }
        return new EventFlow(flowId, executionContext, steps);
    }
}
