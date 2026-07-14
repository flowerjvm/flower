package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class DurableRecoveryPolicyRule extends AbstractFactRule {

    public DurableRecoveryPolicyRule() {
        super("FLOWER-CHECK-005", Severity.ERROR,
                "Durable Flow steps must declare a recovery policy",
                AnalysisFact.DURABLE_STEP_MISSING_RECOVERY);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return "Durable flow step '" + fact.subject() + "' has no statically resolved recovery policy.";
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Durable mode is checkpoint/resume. After restart, Flower must know whether the "
                + "step can re-enter onEnter or must resume through DurableStep.onResume.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Use durableStep(stepId, step, RecoveryPolicy.REENTER_IDEMPOTENT), or make the "
                + "step extend DurableStep with the right RecoveryPolicy.";
    }
}
