package io.github.parkkevinsb.flower.observability.support;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.event.EventHandler;
import io.github.parkkevinsb.flower.core.event.Subscription;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.time.Clock;

/**
 * Minimal {@link StepContext} for unit tests in this module. Only the
 * identification methods are populated; navigation / event / signal / timeout
 * APIs throw {@link UnsupportedOperationException} because the observability
 * helpers under test do not call them.
 */
public final class FakeStepContext implements StepContext {

    private final FlowId flowId;
    private final String stepId;
    private final int stepNo;

    public FakeStepContext(FlowId flowId, String stepId, int stepNo) {
        this.flowId = flowId;
        this.stepId = stepId;
        this.stepNo = stepNo;
    }

    @Override public FlowId flowId() { return flowId; }
    @Override public String currentStepId() { return stepId; }
    @Override public int stepNo() { return stepNo; }

    @Override public void setStepNo(int stepNo) { throw unsupported(); }
    @Override public <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler) { throw unsupported(); }
    @Override public EventBus eventBus() { throw unsupported(); }
    @Override public void signal(String name) { throw unsupported(); }
    @Override public <E> void signal(String name, E payload) { throw unsupported(); }
    @Override public boolean hasSignal(String name) { throw unsupported(); }
    @Override public <E> E signalPayload(String name, Class<E> type) { throw unsupported(); }
    @Override public <E> E consumeSignal(String name, Class<E> type) { throw unsupported(); }
    @Override public void clearSignal(String name) { throw unsupported(); }
    @Override public void startTimeout(long millis) { throw unsupported(); }
    @Override public boolean timedOut() { throw unsupported(); }
    @Override public long elapsedMillis() { throw unsupported(); }
    @Override public Clock clock() { throw unsupported(); }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not used by observability tests");
    }
}
