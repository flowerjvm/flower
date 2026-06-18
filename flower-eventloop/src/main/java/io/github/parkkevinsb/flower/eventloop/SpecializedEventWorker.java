package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;

import java.util.List;

/**
 * Base class for role-specific event-loop worker facades.
 *
 * <p>The specialized workers do not change the event-loop semantics. They are
 * thin, named delegates that make LLM, agent, and MCP runtime wiring explicit
 * while keeping all execution in {@link EventWorker}.
 */
public abstract class SpecializedEventWorker implements EventWorkerHandle {

    private final String role;
    private final EventWorker delegate;
    private final EventFlowCheckpointStore checkpointStore;

    protected SpecializedEventWorker(
            String role,
            EventWorker delegate,
            EventFlowCheckpointStore checkpointStore) {
        if (role == null || role.isEmpty()) {
            throw new IllegalArgumentException("role must not be null or empty");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (checkpointStore == null) {
            throw new IllegalArgumentException("checkpointStore must not be null");
        }
        this.role = role;
        this.delegate = delegate;
        this.checkpointStore = checkpointStore;
    }

    public String role() {
        return role;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public List<FlowerListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public EventWorker delegate() {
        return delegate;
    }

    public EventFlowCheckpointStore checkpointStore() {
        return checkpointStore;
    }

    public EventFlow recover(EventFlowCheckpoint checkpoint, EventFlowFactoryRegistry registry) {
        return recover(checkpoint, registry, DuplicatePolicy.REJECT);
    }

    public EventFlow recover(
            EventFlowCheckpoint checkpoint,
            EventFlowFactoryRegistry registry,
            DuplicatePolicy policy) {
        return recoveryService(registry).recover(checkpoint, delegate, policy);
    }

    public int recoverActive(EventFlowFactoryRegistry registry) {
        return recoverActive(registry, DuplicatePolicy.REJECT);
    }

    public int recoverActive(EventFlowFactoryRegistry registry, DuplicatePolicy policy) {
        return recoveryService(registry).recoverActiveForWorker(delegate, policy);
    }

    @Override
    public void submit(EventFlow flow) {
        delegate.submit(flow);
    }

    @Override
    public void submit(EventFlow flow, DuplicatePolicy policy) {
        delegate.submit(flow, policy);
    }

    @Override
    public boolean cancel(FlowId flowId) {
        return delegate.cancel(flowId);
    }

    @Override
    public FlowState stateOf(FlowId flowId) {
        return delegate.stateOf(flowId);
    }

    @Override
    public int activeCount() {
        return delegate.activeCount();
    }

    @Override
    public void signal(String signalName, String signalKey) {
        delegate.signal(signalName, signalKey);
    }

    @Override
    public void signal(String signalName, String signalKey, Object payload) {
        delegate.signal(signalName, signalKey, payload);
    }

    @Override
    public void drain() {
        delegate.drain();
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    private EventFlowRecoveryService recoveryService(EventFlowFactoryRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        return EventFlowRecoveryService.create(checkpointStore, registry);
    }
}
