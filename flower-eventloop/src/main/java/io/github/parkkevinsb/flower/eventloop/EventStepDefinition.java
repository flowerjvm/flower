package io.github.parkkevinsb.flower.eventloop;

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
}
