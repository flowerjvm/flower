package io.github.flowerjvm.flower.core.flow;

import io.github.flowerjvm.flower.core.step.RecoveryPolicy;
import io.github.flowerjvm.flower.core.step.StepDefinition;

/**
 * Read-only description of one Step declared in a Flow.
 *
 * <p>This is definition metadata, not execution history. It is intended for
 * dump/admin views that need to show the static Step order and highlight the
 * current Step from {@link FlowSnapshot#currentStepIndex()}.
 */
public final class FlowStepSnapshot {

    private final int index;
    private final String stepId;
    private final String stepType;
    private final boolean guarded;
    private final boolean recoverable;
    private final String recoveryPolicy;

    public FlowStepSnapshot(
            int index,
            String stepId,
            String stepType,
            boolean guarded,
            boolean recoverable,
            String recoveryPolicy) {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative: " + index);
        }
        if (stepId == null || stepId.isEmpty()) {
            throw new IllegalArgumentException("stepId must not be null or empty");
        }
        this.index = index;
        this.stepId = stepId;
        this.stepType = stepType;
        this.guarded = guarded;
        this.recoverable = recoverable;
        this.recoveryPolicy = recoveryPolicy;
    }

    static FlowStepSnapshot from(int index, StepDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        RecoveryPolicy policy = definition.recoveryPolicy();
        return new FlowStepSnapshot(
                index,
                definition.stepId(),
                definition.step().getClass().getName(),
                definition.guard() != null,
                definition.recoverable(),
                policy == null ? null : policy.name());
    }

    public int index() {
        return index;
    }

    public String stepId() {
        return stepId;
    }

    public String stepType() {
        return stepType;
    }

    public boolean guarded() {
        return guarded;
    }

    public boolean recoverable() {
        return recoverable;
    }

    public String recoveryPolicy() {
        return recoveryPolicy;
    }
}
