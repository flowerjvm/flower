package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class DurableEventStepRecoveryRule extends AbstractFactRule {

    public DurableEventStepRecoveryRule() {
        super("FLOWER-CHECK-019", Severity.ERROR,
                "Durable EventStep awaits must be recoverable",
                AnalysisFact.DURABLE_EVENT_STEP_NOT_RECOVERABLE);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return "Durable EventFlow step '" + fact.subject() + "' is not recoverable: "
                + fact.detail() + ".";
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "EventStep.onRecover defaults to failure, and predicate lambdas cannot be "
                + "serialized by the current event-loop checkpoint format.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Override onRecover(...) to recreate pending awaits without repeating one-shot "
                + "effects. Use exact event waits or durable signal name/key correlation.";
    }
}
