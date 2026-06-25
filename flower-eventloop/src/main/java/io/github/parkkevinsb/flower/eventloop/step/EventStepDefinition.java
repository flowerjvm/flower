package io.github.parkkevinsb.flower.eventloop.step;

import io.github.parkkevinsb.flower.eventloop.recovery.EventRecoveryContext;

/** Pairs a flow-level step id with its {@link EventStep} instance. */
public final class EventStepDefinition {

    private final String stepId;
    private final EventStep step;

    public EventStepDefinition(String stepId, EventStep step) {
        if (stepId == null || stepId.isEmpty()) {
            throw new IllegalArgumentException("stepId must not be null or empty");
        }
        if (step == null) {
            throw new IllegalArgumentException("step must not be null");
        }
        this.stepId = stepId;
        this.step = step;
    }

    public String stepId() {
        return stepId;
    }

    public EventStep step() {
        return step;
    }

    public EventStepResult enter(EventStepContext ctx) {
        return step.onEnter(ctx);
    }

    public EventStepResult event(EventStepContext ctx, Object event) {
        return step.onEvent(ctx, event);
    }

    public EventStepResult timeout(EventStepContext ctx) {
        return step.onTimeout(ctx);
    }

    public EventStepResult recover(EventStepContext ctx, EventRecoveryContext recovery) {
        return step.onRecover(ctx, recovery);
    }

    public void exit(EventStepContext ctx) {
        step.onExit(ctx);
    }
}
