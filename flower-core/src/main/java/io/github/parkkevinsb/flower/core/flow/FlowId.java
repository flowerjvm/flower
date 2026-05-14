package io.github.parkkevinsb.flower.core.flow;

import java.util.Objects;

/**
 * Identifies a {@link Flow} instance by its {@code flowType} and {@code flowKey}.
 *
 * <p>{@code flowType} groups flows of the same kind (e.g. {@code "quay-work"}).
 * {@code flowKey} is the domain instance key (e.g. a work-order id) that
 * makes the flow unique inside its type.
 */
public final class FlowId {

    private final String flowType;
    private final String flowKey;

    public FlowId(String flowType, String flowKey) {
        if (flowType == null || flowType.isEmpty()) {
            throw new IllegalArgumentException("flowType must not be null or empty");
        }
        if (flowKey == null || flowKey.isEmpty()) {
            throw new IllegalArgumentException("flowKey must not be null or empty");
        }
        this.flowType = flowType;
        this.flowKey = flowKey;
    }

    public static FlowId of(String flowType, String flowKey) {
        return new FlowId(flowType, flowKey);
    }

    public String flowType() {
        return flowType;
    }

    public String flowKey() {
        return flowKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowId)) return false;
        FlowId that = (FlowId) o;
        return flowType.equals(that.flowType) && flowKey.equals(that.flowKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowType, flowKey);
    }

    @Override
    public String toString() {
        return flowType + "/" + flowKey;
    }
}
