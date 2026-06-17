package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.flow.FlowId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of durable event-flow factories keyed by {@code flowType}.
 */
public final class EventFlowFactoryRegistry {

    private final Map<String, EventFlowFactory> factories;

    private EventFlowFactoryRegistry(Map<String, EventFlowFactory> factories) {
        this.factories = Collections.unmodifiableMap(new LinkedHashMap<>(factories));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean contains(String flowType) {
        return factories.containsKey(flowType);
    }

    public Set<String> flowTypes() {
        return factories.keySet();
    }

    public EventFlow create(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        EventFlowFactory factory = factories.get(flowId.flowType());
        if (factory == null) {
            throw new IllegalStateException("No EventFlowFactory registered for flowType: " + flowId.flowType());
        }
        EventFlow flow = factory.create(flowId);
        if (flow == null) {
            throw new IllegalStateException("EventFlowFactory returned null for flowId: " + flowId);
        }
        return flow;
    }

    public EventFlow recover(EventFlowCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint must not be null");
        }
        return create(checkpoint.flowId()).recoverFrom(checkpoint);
    }

    public static final class Builder {
        private final Map<String, EventFlowFactory> factories = new LinkedHashMap<>();

        public Builder register(String flowType, EventFlowFactory factory) {
            if (flowType == null || flowType.isEmpty()) {
                throw new IllegalArgumentException("flowType must not be null or empty");
            }
            if (factory == null) {
                throw new IllegalArgumentException("factory must not be null");
            }
            if (factories.containsKey(flowType)) {
                throw new IllegalArgumentException("duplicate EventFlowFactory for flowType: " + flowType);
            }
            factories.put(flowType, factory);
            return this;
        }

        public EventFlowFactoryRegistry build() {
            return new EventFlowFactoryRegistry(factories);
        }
    }
}
