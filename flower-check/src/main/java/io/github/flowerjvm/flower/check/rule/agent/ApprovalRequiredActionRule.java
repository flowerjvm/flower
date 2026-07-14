package io.github.flowerjvm.flower.check.rule.agent;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class ApprovalRequiredActionRule extends AbstractFactRule {

    public ApprovalRequiredActionRule() {
        super("FLOWER-CHECK-008", Severity.ERROR,
                "Approval-required action must not execute directly",
                AnalysisFact.APPROVAL_DIRECT_EXECUTION);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Approval-gated work must wait for an approval decision. Executing the write "
                + "inline defeats the gate.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Park the action for approval, resume after the approval signal/state, then "
                + "dispatch through the gated registry path.";
    }
}
