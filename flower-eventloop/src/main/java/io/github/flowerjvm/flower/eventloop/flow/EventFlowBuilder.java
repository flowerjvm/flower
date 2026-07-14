package io.github.flowerjvm.flower.eventloop.flow;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowPersistence;
import io.github.flowerjvm.flower.eventloop.step.EventStep;
import io.github.flowerjvm.flower.eventloop.step.EventStepDefinition;

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
    private FlowPersistence persistence = FlowPersistence.TRANSIENT;
    private String definitionVersion;

    EventFlowBuilder(String flowType, String flowKey) {
        this.flowId = FlowId.of(flowType, flowKey);
    }

    public EventFlowBuilder executionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext == null ? ExecutionContext.empty() : executionContext;
        return this;
    }

    public EventFlowBuilder durable() {
        return persistence(FlowPersistence.DURABLE);
    }

    public EventFlowBuilder persistence(FlowPersistence persistence) {
        if (persistence == null) {
            throw new IllegalArgumentException("persistence must not be null");
        }
        this.persistence = persistence;
        return this;
    }

    public EventFlowBuilder definitionVersion(String definitionVersion) {
        if (definitionVersion != null && definitionVersion.isEmpty()) {
            throw new IllegalArgumentException("definitionVersion must not be empty");
        }
        this.definitionVersion = definitionVersion;
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
        return new EventFlow(flowId, executionContext, steps, persistence, definitionVersion);
    }
}
