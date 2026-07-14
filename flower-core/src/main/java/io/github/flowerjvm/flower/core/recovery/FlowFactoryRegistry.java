package io.github.flowerjvm.flower.core.recovery;

import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of Flow factories keyed by {@code flowType}.
 *
 * <p>This is intentionally small: Flower does not discover Flow definitions or
 * replay old execution. The host application registers the Flow builders it
 * wants to make recoverable.
 */
public final class FlowFactoryRegistry {

    private final Map<String, FlowFactory> factories;

    private FlowFactoryRegistry(Map<String, FlowFactory> factories) {
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

    public Flow create(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        FlowFactory factory = factories.get(flowId.flowType());
        if (factory == null) {
            throw new IllegalStateException("No FlowFactory registered for flowType: " + flowId.flowType());
        }
        Flow flow = factory.create(flowId);
        if (flow == null) {
            throw new IllegalStateException("FlowFactory returned null for flowId: " + flowId);
        }
        return flow;
    }

    public Flow recover(FlowCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint must not be null");
        }
        return create(checkpoint.flowId()).recoverFrom(checkpoint);
    }

    public static final class Builder {
        private final Map<String, FlowFactory> factories = new LinkedHashMap<>();

        public Builder register(String flowType, FlowFactory factory) {
            if (flowType == null || flowType.isEmpty()) {
                throw new IllegalArgumentException("flowType must not be null or empty");
            }
            if (factory == null) {
                throw new IllegalArgumentException("factory must not be null");
            }
            if (factories.containsKey(flowType)) {
                throw new IllegalArgumentException("duplicate FlowFactory for flowType: " + flowType);
            }
            factories.put(flowType, factory);
            return this;
        }

        public FlowFactoryRegistry build() {
            return new FlowFactoryRegistry(factories);
        }
    }
}
