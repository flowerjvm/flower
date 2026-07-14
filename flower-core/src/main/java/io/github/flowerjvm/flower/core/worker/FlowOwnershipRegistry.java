package io.github.flowerjvm.flower.core.worker;

import io.github.flowerjvm.flower.core.flow.FlowId;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks which Worker owns a FlowId within one Engine.
 */
public final class FlowOwnershipRegistry {

    private final ConcurrentMap<FlowId, String> owners = new ConcurrentHashMap<>();

    public Claim claim(FlowId flowId, String workerName) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        if (workerName == null || workerName.isEmpty()) {
            throw new IllegalArgumentException("workerName must not be null or empty");
        }
        String existing = owners.putIfAbsent(flowId, workerName);
        if (existing == null) {
            return new Claim(true, true, workerName);
        }
        if (workerName.equals(existing)) {
            return new Claim(true, false, existing);
        }
        return new Claim(false, false, existing);
    }

    public boolean release(FlowId flowId, String workerName) {
        if (flowId == null || workerName == null) {
            return false;
        }
        return owners.remove(flowId, workerName);
    }

    public String ownerOf(FlowId flowId) {
        return flowId == null ? null : owners.get(flowId);
    }

    public static final class Claim {
        private final boolean accepted;
        private final boolean newlyClaimed;
        private final String ownerName;

        private Claim(boolean accepted, boolean newlyClaimed, String ownerName) {
            this.accepted = accepted;
            this.newlyClaimed = newlyClaimed;
            this.ownerName = ownerName;
        }

        public boolean accepted() {
            return accepted;
        }

        public boolean newlyClaimed() {
            return newlyClaimed;
        }

        public String ownerName() {
            return ownerName;
        }
    }
}
