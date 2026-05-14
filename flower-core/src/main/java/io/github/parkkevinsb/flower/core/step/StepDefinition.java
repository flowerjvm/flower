package io.github.parkkevinsb.flower.core.step;

/**
 * Pairs a Flow-level Step id with its {@link Step} instance.
 *
 * <p>Flow-level ids must be unique within a Flow. The id is used by
 * {@link StepResult#goTo(String)} to jump between Steps without relying
 * on the Step's class type, which makes it possible to use the same
 * Step class more than once in a Flow.
 */
public final class StepDefinition {

    private final String stepId;
    private final Step step;
    private final Guard guard;

    public StepDefinition(String stepId, Step step) {
        this(stepId, step, null);
    }

    public StepDefinition(String stepId, Step step, Guard guard) {
        if (stepId == null || stepId.isEmpty()) {
            throw new IllegalArgumentException("stepId must not be null or empty");
        }
        if (step == null) {
            throw new IllegalArgumentException("step must not be null");
        }
        this.stepId = stepId;
        this.step = step;
        this.guard = guard;
    }

    public String stepId() {
        return stepId;
    }

    public Step step() {
        return step;
    }

    public Guard guard() {
        return guard;
    }

    @Override
    public String toString() {
        return "StepDefinition{" + stepId + " -> " + step.getClass().getSimpleName() + "}";
    }
}
